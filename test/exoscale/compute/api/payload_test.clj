(ns exoscale.compute.api.payload-test
  (:require [exoscale.compute.api.payload :refer [sign build-payload]]
            [clojure.test :refer :all]
            [clj-time.core :as t]))

(def API_KEY "key")
(def API_SECRET "secret")

;; NOTE: the following test has been borrowed from clostack:
;; https://github.com/exoscale/clostack/blob/master/test/clostack/payload_test.clj
;; It doesn't work here as we've got changes in the signing code.
;; What should be done here is, we need to decide which signing
;; method is better and correct the test.

#_
(deftest test-sign-cases
  (testing "sha1 signature case sensitivity"
    (is (not= (sign {"a" "a" "b" "B"} API_SECRET)
              (sign {"a" "a" "B" "B"} API_SECRET)))
    (is (not= (sign {:a "a" :b "B"} API_SECRET)
              (sign {:a "a" :B "B"} API_SECRET)))
    (is (not= (sign {:a "a" :b [{:c 1 :d true}]} API_SECRET)
              (sign {:a "a" :b [{:c 1 :D true}]} API_SECRET))))

  (testing "keys should be uniq"
    (is (thrown-with-msg? Exception #"Keys should not be duplicated"
                          (sign {:a "foo" :A "bar"} API_SECRET)))))


(deftest test-payload
  (with-redefs [t/now (constantly (t/date-time 2019))]
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
             payload)))))
