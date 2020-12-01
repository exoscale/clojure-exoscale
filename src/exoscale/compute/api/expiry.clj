(ns exoscale.compute.api.expiry
  "Date manipulation functions."
  (:import (java.time.format DateTimeFormatter)
           (java.time Instant ZoneOffset)))

(def ^DateTimeFormatter cloudstack-format
  "Date format used by Cloudstack"
  (-> (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssZ")
      (.withZone ZoneOffset/UTC)))

(def default-ttl
  "Default to 5 minute expiry"
  600)

(def base-payload
  "Expires need to be provided in conjunction with signature V3"
  {:signatureVersion "3"})

(defn limit
  "Compute date of request validity expiry."
  [^Instant from ttl-seconds]
  (.format cloudstack-format
           (.plusSeconds from (long ttl-seconds))))

(defn now
  []
  (Instant/now))

(defn args
  "Builds the expires argument map. Expiration is in seconds.
   When expiry is provided, promote a request payload to V3
   signatures."
  ([ttl]
   (args ttl (now)))
  ([ttl from]
   (cond
     (false? ttl)  nil
     (nil? ttl)    (assoc base-payload :expires (limit from default-ttl))
     (number? ttl) (assoc base-payload :expires (limit from ttl))
     :else         (throw (IllegalArgumentException. "cannot infer ttl")))))
