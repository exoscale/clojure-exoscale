(ns exoscale.compute.api.http
  "HTTP support for the Exoscale Compute API"
  (:require [cheshire.core                :as json]
            [clojure.java.io :as io]
            [clojure.string               :as str]
            [exoscale.compute.api.payload :as payload]
            [exoscale.telex               :as client]
            [qbits.auspex                 :as auspex])
  (:import (java.io InputStream)))

(def default-client
  (delay (client/client #:exoscale.telex.client{:connect-timeout 10000})))

(def default-page-size
  "Number of records to fetch by default"
  500)

(def default-poll-interval
  "Default timeout for async requests"
  100)

(def default-max-polls
  "Default number of times to poll before timing out"
  10)

(def default-endpoint
  "Default HTTP endpoint for the Exoscale API"
  "https://api.exoscale.com/compute")

(def default-http-opts
  "Default HTTP options passed to the underlying HTTP library (aleph)."
  {:request-timeout 10000})

(defn with-decoded-error-body
  "Catches potential deferred error and rethrow with decoded
  ex-data.body if there is one, otherwise just rethrow"
  [d]
  (auspex/catch d clojure.lang.ExceptionInfo
    (fn [e]
      (let [d (:response (ex-data e))]
        (throw
         (ex-info (ex-message e)
                  (update d
                          :body
                          #(cond-> %
                             (instance? InputStream %)
                             slurp))
                  (ex-cause e)))))))

(def list-command?
  (memoize
   (fn [opcode]
     (let [opcode (name opcode)]
       (str/starts-with? opcode "list")))))

(defn opts->http-request-opts
  "Converts legacy request opts to jdk11 client opts"
  [{:as _opts :keys [request-timeout]}]
  (cond-> {}
    request-timeout
    (assoc :exoscale.telex.request/timeout request-timeout)))

(defn parse-body [response]
  (update response
          :body
          #(some-> %
                   (io/reader)
                   (json/parse-stream true))))

(defn raw-request!!
  "Send an HTTP request"
  [{:keys [endpoint http-opts client method]
    :or {method :get}
    :as _config} payload]
  (let [opts   (merge default-http-opts http-opts {:as :json})
        method (some-> method name str/lower-case keyword)
        paramk (if (= :get method) :query-params :form-params)]
    (-> (client/request (or client @default-client)
                        (cond-> (assoc (opts->http-request-opts opts)
                                       :url (or endpoint default-endpoint)
                                       paramk payload
                                       :method method)
                          (= :post method)
                          (assoc :headers {:content-type "application/x-www-form-urlencoded"})))
        (auspex/chain parse-body)
        with-decoded-error-body)))

(defn extract-response
  "From a response get the result data"
  [resp opcode]
  (get (:body resp) (-> opcode name str/lower-case (str "response") keyword)))

(defn find-payload
  [resp opcode]
  (let [list?      (list-command? opcode)
        payload    (-> resp (dissoc :count) vals first)
        elem-count (:count resp)]
    (cond
      (and list? (empty? payload)) (with-meta [] {:count elem-count})
      list?                        (with-meta payload {:count elem-count})
      (some? (:jobid resp))        resp
      (map? payload)               payload
      (sequential? payload)        payload
      :else                        resp)))

(defn text-request!!
  [config opcode params]
  (raw-request!! config (payload/build-payload config opcode params)))

(defn json-request!!
  [config opcode params]
  (auspex/chain
   (raw-request!! config (payload/build-payload config opcode params))
   #(extract-response % opcode)
   #(find-payload % opcode)))

(defn list-request!!
  "Perform a paging request. Elements are fetched by chunks of 500.
   If both `page` and `pagesize` are provided then we return a single page only,
   Otherwise fetch all results page by page.

  This functionality is dependent on the api call supporting pagination,
  `listZones` and `listSecurityGroups` are examples of this.

  Custom Exoscale commands that are not present in Cloudstack such as
  `listApiKeys` return the full list and do not support pagination."
  [config opcode {:keys [page pagesize] :or {page 1} :as params}]
  (let [single-page-only? (and (some? pagesize) (some? page))
        pagesize (or pagesize default-page-size)]
    (auspex/loop [page page
                  acc []]
      (auspex/chain (json-request!! config opcode (assoc params
                                                         :page page
                                                         :pagesize pagesize))
                    (fn [resp]
                      (let [acc (concat acc resp)
                            meta-count (:count (meta resp))
                            all-results-present? (= meta-count (count acc))]
                        (if (or (nil? meta-count)
                                single-page-only?
                                all-results-present?
                                (not (seq resp)))
                          (with-meta (vec acc) (meta resp))
                          (auspex/recur (inc page) acc))))))))

(defn wait-or-return-job!!
  [config remaining]
  (let [interval (or (:poll-interval config) default-poll-interval)]
    (fn [{:keys [jobstatus] :as job}]
      (if (zero? jobstatus)
        (auspex/chain (auspex/timeout! (auspex/future)
                                       interval
                                       ::timeout)
                      (fn [_] (auspex/recur (dec remaining))))
        job))))

(defn job-loop!!
  [config]
  (fn [{:keys [jobid] :as resp}]
    (if (some? jobid)
      (auspex/loop [remaining (or (:max-polls config) default-max-polls)]
        (if (pos? remaining)
          ;; The previous response can be used as input
          ;; to queryAsyncJobResult directly
          (auspex/chain (json-request!! config "queryAsyncJobResult" {:jobid jobid})
                        (wait-or-return-job!! config remaining))
          resp))
      resp)))

(defn validate-job!!
  "* return jobresult if job was successful
  * throw ex-info in case of failure (body is a string to mimic
  behavior of other query exceptions)"
  [{{:keys [errortext errorcode] :as jobresult} :jobresult :as response}
   config opcode params]
  (when (and (:throw-on-job-failure? config)
             (some? errortext))
    (throw (ex-info "Job failed"
                    {:status errorcode
                     :body (json/generate-string {:queryasyncjobresultresponse
                                                  response})
                     :command opcode
                     :params params})))
  (find-payload jobresult opcode))

(defn job-request!!
  [config opcode params]
  (auspex/chain (json-request!! config opcode params)
                (job-loop!! config)
                (fn [response]
                  (if (:jobresult response)
                    (validate-job!! response config opcode params)
                    response))))

(defn request!!
  "Send a request to the API and figure out the best course of action
  from there.

  Specifically:

  - For a list request, fetch and page results
  - For an async job request, wait for the job result
  - "
  [config opcode params]
  (if (list-command? opcode)
    (list-request!! config opcode params)
    (job-request!! config opcode params)))
