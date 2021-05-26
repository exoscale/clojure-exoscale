(ns exoscale.compute.api.payload
  "Functions to work with appropriate cloudstack payloads."
  (:require [clojure.string              :as str]
            [exoscale.compute.api.hmac   :as hmac]
            [exoscale.cloak              :as cloak]
            [exoscale.compute.api.expiry :as expiry]))

(defn stringify
  [x]
  (if (keyword? x)
    (name x)
    (str x)))

(defn url-encode
  "Encode URL"
  [s]
  (java.net.URLEncoder/encode (str s) "UTF-8"))

(defn quote-plus
  "Replace + in encoded URL by %20"
  [s]
  (str/replace (str s) "+" "%20"))

(defn serialize-pair
  "Encode a key/value pair"
  [[k v]]
  (str (name k) "=" (url-encode v)))

(defn transform-map
  "For a map, produce the expected key/value pairs."
  [param m i]
  (for [[k v] m]
    [(format "%s[%d].%s" (name param) i (name k))  (stringify v)]))

(defn transform-maps
  "For a list of maps, produce the expected key/value pairs."
  [param ms]
  (mapcat (partial transform-map param) ms (range)))

(defn transform-arg
  "Transform argument into a list of key/value pairs."
  [[k v]]
  (let [k (name k)]
    (when (or (and (sequential? v) (seq v)) (some? v))
      (cond
        (and (sequential? v) (-> v first map?))
        (transform-maps k v)

        (sequential? v)
        (map #(vector k (stringify %)) v)

        :else
        [[k (stringify v)]]))))

(defn query-args
  "Build arguments, ready to be signed."
  [params]
  (->> (mapcat transform-arg params)
       (sort-by first)
       (map serialize-pair)
       (str/join "&")))

(defn sign
  "Sign the given query"
  [query api-secret]
  (hmac/sha1 api-secret (-> query str/lower-case quote-plus)))

(defn assoc-value
  [m k v]
  (cond
    (and (sequential? v) (map? (first v)))
    (merge m (reduce merge {} (transform-maps k v)))

    (sequential? v)
    (assoc m k (str/join "," (map stringify v)))

    :else
    (assoc m k v)))

(defn sanitize
  [params]
  (reduce-kv assoc-value {} params))

(defn build-payload
  "Build a signed payload for a given config, opcode and args triplet"
  ([config opcode params]
   (build-payload config (assoc params :command opcode)))
  ([config params]
   (let [{:keys [api-key api-secret ttl]} (cloak/unmask config)
         params (cloak/unmask params)
         payload (-> (sanitize params)
                     (assoc :apiKey api-key :response "json")
                     (merge (expiry/args ttl)))]
     (cond-> payload
       (not (str/blank? api-secret))
       (assoc :signature (sign (query-args payload) api-secret))))))
