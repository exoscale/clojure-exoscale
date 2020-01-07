(ns exoscale.compute.api.http-test
  (:require [clojure.test :refer :all]
            [exoscale.compute.api.http :as http]))

(deftest find-payload-test
  (is (= {:id "bar"}
         (http/find-payload {:network {:id "bar"}})))
  (is (= [{:id "bar"}]
         (http/find-payload {:network [{:id "bar"}]})))
  (is (= {:success true}
         (http/find-payload {:success true})))
  (is (= {:id "bar"
          :name "foo"}
         (http/find-payload {:id "bar"
                             :name "foo"})))
  (is (= {:jobid "dac8bd04-31ff-11ea-ba11-6bef69b2ab5f"}
         (http/find-payload {:jobid "dac8bd04-31ff-11ea-ba11-6bef69b2ab5f"})))
  (is (= [{:id "bar"}]
         (http/find-payload {:network [{:id "bar"}]
                             :count 1}))))
