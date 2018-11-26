(ns exoscale.compute.api.meta
  "Common metadata access for API types"
  (:refer-clojure :exclude [type])
  (:require [exoscale.compute.api.client :as client]))

(defn zone
  [x]
  (:exoscale.compute/zone (meta x)))

(defn source
  [x]
  (:exoscale.compute/source (meta x)))

(defn type
  [x]
  (:exoscale.compute/type (meta x)))

(defn build-meta
  [{:keys [zoneid] :as source} type]
  (cond-> {:exoscale.compute/type   type
           :exoscale.compute/source source}
    (some? zoneid) (assoc :exoscale.compute/zone (client/parse-uuid zoneid))))

(defn describe
  [x type source]
  (let [details (build-meta source type)]
    (with-meta x details)))
