(ns exoscale.compute.api.expiry
  "Date manipulation functions."
  (:require [clj-time.format :refer [parse unparse formatter formatters]]
            [clj-time.core   :refer [seconds plus now after?]]))

(def cloudstack-format
  "Date format used by Cloudstack"
  (formatter "yyyy-MM-dd'T'HH:mm:ssZ"))

(def default-ttl
  "Default to 5 minute expiry"
  600)

(def base-payload
  "Expires need to be provided in conjunction with signature V3"
  {:signatureVersion "3"})

(defn limit
  "Compute date of request validity expiry"
  [from ttl]
  (unparse cloudstack-format (plus from (seconds ttl))))

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
