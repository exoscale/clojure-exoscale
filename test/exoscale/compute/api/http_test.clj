(ns exoscale.compute.api.http-test
  (:require [clojure.test :refer :all]
            [exoscale.compute.api.http :as http]))

(deftest find-payload-test
  (testing "map with a top level key"
    (is (= {:id "bar"}
           (http/find-payload {:network {:id "bar"}}))))
  (testing "vector with a top level key"
    (is (= [{:id "bar"}]
           (http/find-payload {:network [{:id "bar"}]}))))
  (testing "map with one element"
    (is (= {:success true}
           (http/find-payload {:success true}))))
  (testing "map with multiple elements"
    (is (= {:id "bar"
            :name "foo"}
           (http/find-payload {:id "bar"
                               :name "foo"}))))
  (testing "empty map"
    (is (= {}
           (http/find-payload {}))))
  (testing "map containinb a :jobid key"
    (is (= {:jobid "dac8bd04-31ff-11ea-ba11-6bef69b2ab5f"}
           (http/find-payload {:jobid "dac8bd04-31ff-11ea-ba11-6bef69b2ab5f"}))))
  (testing "vector"
    (is (= [{:id "bar"}]
           (http/find-payload {:network [{:id "bar"}]
                               :count 1})))))
