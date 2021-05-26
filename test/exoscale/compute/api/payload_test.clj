(ns exoscale.compute.api.payload-test
  (:require [exoscale.compute.api.payload :refer [build-payload transform-maps]]
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
          "we can decode cloaked params and config"))

    (let [payload (build-payload
                   {:expiration 3600}
                   :someApiCall
                   {:arg1 "test"
                    :arg2 (cloak/mask 42)})]
      (is (= {:arg1 "test"
              :arg2 42
              :command :someApiCall
              :apiKey nil
              :response "json"
              :signatureVersion "3"
              :expires "2019-01-01T00:10:00+0000"}
             payload)))))

(deftest test-maps-and-colls
  (with-redefs [exp/now (constantly (Instant/ofEpochMilli 1546300800000))]
    (let [payload (build-payload {:api-key API_KEY}
                                 :someApiCall
                                 {:arg1 [{:key "foo" :value "bar"}
                                         {:key "bim" :value "boom"}]
                                  :arg2 ["foo" "bar" "baz"]})]
      (is (= {"arg1[0].key" "foo"
              "arg1[0].value" "bar"
              "arg1[1].key" "bim"
              "arg1[1].value" "boom"
              :arg2 "foo,bar,baz"
              :command :someApiCall
              :apiKey "key"
              :signatureVersion "3"
              :response "json"
              :expires "2019-01-01T00:10:00+0000"}
             payload)))))
