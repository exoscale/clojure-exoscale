(ns exoscale.compute.api.http-test
  (:require [clojure.test :refer [deftest testing is]]
            [qbits.auspex :as auspex]
            [exoscale.compute.api.http :as http]
            [spy.core :as spy]
            [spy.assert :as assert]))

(deftest find-payload-test
  (testing "map with a top level key"
    (is (= {:id "bar"}
           (http/find-payload {:network {:id "bar"}} :someMutation))))
  (testing "vector with a top level key"
    (is (= [{:id "bar"}]
           (http/find-payload {:network [{:id "bar"}]} :someMutation))))
  (testing "map with one element"
    (is (= {:success true}
           (http/find-payload {:success true} :someMutation))))
  (testing "map with multiple elements"
    (is (= {:id "bar"
            :name "foo"}
           (http/find-payload {:id "bar" :name "foo"} :someMutation))))
  (testing "empty map"
    (is (= {}
           (http/find-payload {} :someMutation))))
  (testing "map containinb a :jobid key"
    (is (= {:jobid "dac8bd04-31ff-11ea-ba11-6bef69b2ab5f"}
           (http/find-payload {:jobid "dac8bd04-31ff-11ea-ba11-6bef69b2ab5f"} :someMutation))))
  (testing "vector"
    (is (= [{:id "bar"}]
           (http/find-payload {:network [{:id "bar"}] :count 1} :someMutation)))))

(defn json-request-spy [items]
  (spy/spy (fn [config opcode {:keys [page pagesize]}]
             (auspex/success-future (with-meta (->> items
                                                    (drop (* (dec page) pagesize))
                                                    (take pagesize)
                                                    (map (fn [n]
                                                           {:item n})))
                                      {:count (count items)})))))

(deftest pagination-no-params-test
  (let [spy (json-request-spy (range 0 5))]
    (with-redefs [http/json-request!! spy]
      (let [res @(http/list-request!! {} "listZones" {})]
        (is (= [{:item 0} {:item 1} {:item 2} {:item 3} {:item 4}] res))
        (is (= 5 (:count (meta res))))
        (assert/called-once-with? spy {} "listZones" {:page 1 :pagesize 500})))))

(deftest pagination-first-page-test
  (let [spy (json-request-spy (range 0 5))]
    (with-redefs [http/json-request!! spy]
      (let [res @(http/list-request!! {} "listZones" {:page 1 :pagesize 2})]
        (is (= [{:item 0} {:item 1}] res))
        (is (= 5 (:count (meta res))))
        (assert/called-once-with? spy {} "listZones" {:page 1 :pagesize 2})))))

(deftest pagination-second-page-test
  (let [spy (json-request-spy (range 0 5))]
    (with-redefs [http/json-request!! spy]
      (let [res @(http/list-request!! {} "listZones" {:page 2 :pagesize 2})]
        (is (= [{:item 2} {:item 3}] res))
        (is (= 5 (:count (meta res))))
        (assert/called-once-with? spy {} "listZones" {:page 2 :pagesize 2})))))

(deftest pagination-last-page-test
  (let [spy (json-request-spy (range 0 5))]
    (with-redefs [http/json-request!! spy]
      (let [res @(http/list-request!! {} "listZones" {:page 3 :pagesize 2})]
        (is (= [{:item 4}] res))
        (is (= 5 (:count (meta res))))
        (assert/called-once-with? spy {} "listZones" {:page 3 :pagesize 2})))))

(deftest pagination-max-500-test
  (let [spy (json-request-spy (range 0 600))]
    (with-redefs [http/json-request!! spy]
      ;; When no page or pagesize are passed then we should load everything
      ;; in batches of 500
      (let [res @(http/list-request!! {} "listZones" {})]
        (is (= 600 (count res)))
        (is (= 600 (:count (meta res))))
        (assert/called-with? spy {} "listZones" {:page 1 :pagesize 500})
        (assert/called-with? spy {} "listZones" {:page 2 :pagesize 500})))))

(deftest do-not-recur-if-count-missing-test
  (let [spy (spy/stub (auspex/success-future (with-meta [:foo :bar :baz] {:count nil})))]
    (with-redefs [http/json-request!! spy]
      (let [res @(http/list-request!! {} "listZones" {})]
        (is (= [:foo :bar :baz] res))
        (is (= nil (:count (meta res))))
        (assert/called-once-with? spy {} "listZones" {:page 1 :pagesize 500}))))

  (let [spy (spy/stub (auspex/success-future [:foo :bar :baz]))]
    (with-redefs [http/json-request!! spy]
      (let [res @(http/list-request!! {} "listZones" {})]
        (is (= [:foo :bar :baz] res))
        (is (= nil (:count (meta res))))
        (assert/called-once-with? spy {} "listZones" {:page 1 :pagesize 500}))))

  (let [spy (spy/stub (auspex/success-future (with-meta [:foo :bar :baz] {:count 6})))]
    (with-redefs [http/json-request!! spy]
      (let [res @(http/list-request!! {} "listZones" {})]
        ;; the repetition of results may look bizarre, but it's the responsibility of the service
        ;;  we are calling, for this test we specify there are 6 items in the `with-meta`
        ;; count and we recur until we hit that number, the fact the results are repeated is not
        ;; the concern of clojure-exoscale but of the service we are calling. Our stub returns the
        ;; same results every time as opposed to the `json-request-spy` example which mimics real pagination
        (is (= [:foo :bar :baz :foo :bar :baz] res))
        (is (= 6 (:count (meta res))))
        (assert/called-n-times? spy 2)
        (assert/called-with? spy {} "listZones" {:page 1 :pagesize 500})
        (assert/called-with? spy {} "listZones" {:page 2 :pagesize 500})))))
