(ns exoscale.compute.api.payload-test
  (:require [exoscale.compute.api.payload :refer [build-payload]]
            [exoscale.compute.api.expiry :as exp]
            [clojure.test :refer :all]
            [exoscale.cloak :as cloak])
  (:import (java.time Instant)))

(def API_KEY "key")
(def API_SECRET "secret")

(deftest test-payload
  (with-redefs [exp/now (constantly (Instant/ofEpochMilli 1546300800000))]
    (let [payload (build-payload
                   {:api-key API_KEY
                    :api-secret API_SECRET
                    :expiration 3600}
                   :someApiCall
                   {:arg1 "test"
                    :arg2 42})]

      (is (= {:arg1 "test"
              :arg2 42
              :command :someApiCall
              :apiKey "key"
              :response "json"
              :signatureVersion "3"
              :expires "2019-01-01T00:10:00+0000"
              :signature "O0Dpq8eI8F+UbjqFs0LOhgQXfV0="}
             payload)))

    (let [payload (build-payload
                   {:api-key (cloak/mask API_KEY)
                    :api-secret (cloak/mask API_SECRET)
                    :expiration 3600}
                   :someApiCall
                   {:arg1 "test"
                    :arg2 (cloak/mask 42)})]
      (is (= {:arg1 "test"
              :arg2 42
              :command :someApiCall
              :apiKey "key"
              :response "json"
              :signatureVersion "3"
              :expires "2019-01-01T00:10:00+0000"
              :signature "O0Dpq8eI8F+UbjqFs0LOhgQXfV0="}
             payload)
          "we can decode cloaked params and config"))))
