(ns exoscale.compute.api.http
  "HTTP support for the Exoscale Compute API"
  (:require [clojure.string               :as str]
            [aleph.http                   :as http]
            [byte-streams                 :as bs]
            [manifold.deferred            :as d]
            [manifold.time                :as t]
            [exoscale.compute.api.payload :as payload]))

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
  {:connection-timeout 10000
   :request-timeout    10000
   :pool-timeout       1000
   :read-timeout       10000})

(defn with-decoded-error-body
  "Catches potential deferred error and rethrow with decoded
  ex-data.body if there is one, otherwise just rethrow"
  [d]
  (d/catch d clojure.lang.ExceptionInfo
    (fn [e]
      (let [d (ex-data e)]
        (throw (if-let [body (:body d)]
                 (ex-info (ex-message e)
                          (assoc d :body (bs/to-string body))
                          (ex-cause e))
                 e))))))

(def list-command?
  (memoize
   (fn [opcode]
     (-> opcode name (str/starts-with? "list")))))

(defn raw-request!!
  "Send an HTTP request with manifold"
  [{:keys [endpoint http-opts] :as config} payload]
  (let [opts   (merge default-http-opts http-opts {:as :json})
        method (some-> config :request-method name str/lower-case keyword)
        reqfn  (if (= :get method) http/get http/post)
        paramk (if (= :get method) :query-params :form-params)]
    (-> (reqfn (or endpoint default-endpoint) (assoc opts paramk payload))
        with-decoded-error-body)))

(defn extract-response
  "From a response get the result data"
  [resp opcode]
  (get (:body resp) (-> opcode name str/lower-case (str "response") keyword)))

(defn find-payload
  [resp opcode]
  (let [list?      (list-command? opcode)
        ks         (set (keys resp))
        payload    (-> resp (dissoc :count) vals first)
        elem-count (or (:count resp) 0)]
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
  (d/chain
   (raw-request!! config (payload/build-payload config opcode params))
   #(extract-response % opcode)
   #(find-payload % opcode)))

(defn list-request!!
  "Perform a paging request. Elements are fetched by chunks of 500."
  [config
   opcode
   {:keys [page pagesize]
    :or   {page 1}
    :as   params}]
  (let [single-page-only? (some? pagesize)
        pagesize (or pagesize default-page-size)]
    (d/loop [page    page
             result  []]
      (d/chain (json-request!! config
                               opcode
                               (assoc params :page page :pagesize pagesize))
               (fn [resp]
                 (let [result (concat result resp)
                       all-results-present? (= (:count (meta resp)) (count resp))]
                   (if (or single-page-only?
                           all-results-present?
                           (not (seq resp)))
                     (with-meta (vec result) (meta resp))
                     (d/recur (inc page) result))))))))

(defn wait-or-return-job!!
  [config remaining opcode]
  (let [interval (or (:poll-interval config) default-poll-interval)]
    (fn [{:keys [jobstatus] :as job}]
      (if (zero? jobstatus)
        (d/chain (t/in interval (constantly nil))
                 (fn [_] (d/recur (dec remaining))))
        (find-payload (:jobresult job) opcode)))))

(defn job-loop!!
  [config opcode]
  (fn [{:keys [jobid] :as resp}]
    (if (some? jobid)
      (d/loop [remaining (or (:max-polls config) default-max-polls)]
        (if (pos? remaining)
          ;; The previous response can be used as input
          ;; to queryAsyncJobResult directly
          (d/chain (json-request!! config "queryAsyncJobResult" {:jobid jobid})
                   (wait-or-return-job!! config remaining opcode))
          resp))
      resp)))

(defn job-request!!
  [config opcode params]
  (d/chain (json-request!! config opcode params)
           (job-loop!! config opcode)))

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
