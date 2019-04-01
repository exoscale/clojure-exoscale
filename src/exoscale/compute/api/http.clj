(ns exoscale.compute.api.http
  "HTTP support for the Exoscale Compute API"
  (:require [clojure.string               :as str]
            [aleph.http                   :as http]
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

(def entity-special-cases
  {"resetPasswordForVirtualMachine"  [:reset-password :virtualmachine]
   "changeServiceForVirtualMachine"  [:change-service :virtualmachine]
   "migrateVirtualMachineWithVolume" [:migrate-with-volume :virtualmachine]})

(def special-plurals
  {"publicipaddresses" "publicipaddress"
   "oscategories"      "oscategory"
   "capabilities"      "capability"})

(defn depluralize
  [s]
  (let [word (str/lower-case (reduce str "" s))]
    (or (get special-plurals word)
        (drop-last s))))

(def action-entity
  "Yield action type and target entity"
  ;; Results do not change over time and input cardinality is
  ;; small.
  (memoize
   (fn [opcode]
     (or (get entity-special-cases opcode)
         (let [keywordize      #(-> (reduce str %) str/lower-case keyword)
               lower-case?     #(Character/isLowerCase (char %))
               [action entity] (split-with lower-case? opcode)
               plural?         (= \s (last entity))]
           (mapv keywordize [action (cond->> entity plural? depluralize)]))))))

(defn raw-request!!
  "Send an HTTP request with manifold"
  [{:keys [endpoint http-opts] :as config} payload]
  (let [opts   (merge http-opts {:as :json})
        method (some-> config :request-method name str/lower-case keyword)
        reqfn  (if (= :get method) http/get http/post)
        paramk (if (= :get method) :query-params :form-params)]
    (reqfn (or endpoint default-endpoint) (assoc opts paramk payload))))

(defn extract-response
  "From a response get the result data"
  [resp opcode]
  (get (:body resp) (-> opcode name str/lower-case (str "response") keyword)))

(defn transform
  ""
  [resp opcode]
  (let [[action entity] (action-entity opcode)
        payload         (or (get resp entity) resp)]
    (cond-> payload
      (= :list action)
      (with-meta (select-keys resp [:count])))))

(defn text-request!!
  [config opcode params]
  (raw-request!! config (payload/build-payload config opcode params)))

(defn json-request!!
  [config opcode params]
  (d/chain
   (raw-request!! config (payload/build-payload config opcode params))
   #(extract-response % opcode)
   #(transform % opcode)
   ))

(defn list-pager-fn
  [opcode page prev-result]
  (fn [resp]
    (let [pending (- (:count (meta resp) 0) (count resp))
          result  (concat prev-result resp)]
      (if (and (seq resp) (pos? pending))
        (d/recur (inc page)  result pending)
        (vec result)))))

(defn list-request!!
  "Perform a paging request. Elements are fetched by chunks of 500."
  [config opcode params]
  (let [ps (or (:page-size config) default-page-size)]
    (d/loop [page    1
             result  []
             pending nil]
      (let [paged-params (assoc params :page page :pagesize (or pending ps))]
        (d/chain (json-request!! config opcode paged-params)
                 (list-pager-fn opcode page result))))))

(defn job-result
  [opcode {:keys [jobresult]}]
  (let [[_ entity] (action-entity opcode)]
    (or (get jobresult entity) jobresult)))

(defn wait-or-return-job!!
  [config remaining opcode]
  (let [interval (or (:poll-interval config) default-poll-interval)]
    (fn [{:keys [jobstatus] :as job}]
      (if (zero? jobstatus)
        (d/chain (t/in interval (constantly nil))
                 (fn [_] (d/recur (dec remaining))))
        (job-result opcode job)))))

(defn job-loop!!
  [config opcode]
  (fn [{:keys [jobid] :as resp}]
    (if (some? jobid)
      (d/loop [remaining (or (:max-polls config) default-max-polls)]
        ;; The previous response can be used as input
        ;; to queryAsyncJobResult directly
        (d/chain (json-request!! config "queryAsyncJobResult" resp)
                 (wait-or-return-job!! config remaining opcode)))
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
  (let [[action] (action-entity opcode)]
    (if (= :list action)
      (list-request!! config opcode params)
      (job-request!! config opcode params))))
