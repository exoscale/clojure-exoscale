(ns exoscale.compute.api.payload
  "Functions to work with appropriate cloudstack payloads."
  (:require [clojure.string              :as str]
            [exoscale.compute.api.hmac   :as hmac]
            [exoscale.compute.api.expiry :as expiry]))

(defn url-encode
  "Encode URL"
  [s]
  (java.net.URLEncoder/encode s "UTF-8"))

(defn quote-plus
  "Replace + in encoded URL by %20"
  [s]
  (str/replace (str s) "+" "%20"))

(defn serialize-pair
  "Encode a key/value pair"
  [[k v]]
  (str (name k) "=" (url-encode v)))

(defn transform-maps
  "For a list of maps, produce the expected key/value pairs."
  [param maps]
  (for [[[k v] i] (partition 2 (interleave maps (range)))]
    [(format "%s[%d].%s" param i (name k) (str v))]))

(defn transform-arg
  "Transform argument into a list of key/value pairs."
  [[k v]]
  (let [k (name k)
        v (if (keyword? v) (name v) v)]
    (when (or (and (sequential? v) (seq v)) (some? v))
      (cond
        (and (sequential? v) (-> v first map?))
        (transform-maps (name k) v)

        (sequential? v)
        (map #(vector k (if (keyword? %) (name %) (str %))) v)

        :else
        [[k (str v)]]))))

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
  (hmac/sha1 api-secret (-> query str/lower-case quote-plus))
;;  (-> query str/lower-case quote-plus)
  )

(defn build-payload
  "Build a signed payload for a given config, opcode and args triplet"
  ([config opcode params]
    (build-payload config (assoc params :command opcode)))
  ([{:keys [api-key api-secret ttl]} params]
   (let [payload (merge (expiry/args ttl)
                        (assoc params :apikey api-key :response "json"))]
     (assoc payload :signature (sign (query-args payload) api-secret))
     ;;(sign (query-args payload) api-secret)
     )))
