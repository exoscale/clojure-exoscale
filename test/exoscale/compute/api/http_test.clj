(ns exoscale.compute.api.http-test
  (:require [clojure.test :refer [deftest testing is]]
            [qbits.auspex :as auspex]
            [exoscale.compute.api.http :as http]
            [exoscale.compute.api.utils-test :as utils]
            [cheshire.core :as json]
            [spy.core :as spy]
            [spy.assert :as assert])
  (:import (java.io InputStream)))

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

(defn rand-uuid [] (java.util.UUID/randomUUID))

(deftest throw-on-job-failure-when-configured
  (let [job-id (rand-uuid)
        job-result {:errortext "Failed to delete snapshot:com.cloud.exception.InvalidParameterValueException: Can't delete snapshot 75136 with status BackingUp"
                    :errorcode 530}
        job-resp {:queryasyncjobresultresponse
                  {:cmd "org.apache.cloudstack.api.command.user.snapshot.DeleteSnapshotCmd"
                   :jobid  (str job-id)
                   :jobstatus 2
                   :jobresult job-result}}]
    (utils/with-server 8080 {:deleteSnapshot       (constantly {:status 200
                                                                :body {:deletesnapshotresponse
                                                                       {:jobid job-id}}})
                             :queryAsyncJobResult (constantly {:status 200
                                                               :body job-resp})}
      (try
        (deref (http/request!! {:endpoint "http://localhost:8080"
                                :api-key "foo"
                                :api-secret "bar"
                                :throw-on-job-failure? true}
                               "deleteSnapshot"
                               {:id (rand-uuid)}))
        (is false "call should have failed")
        (catch Exception e
          (let [{:keys [status body]} (ex-data (ex-cause e))]
            (is (= 530
                   status))
            (is (= job-resp
                   (json/parse-string body true))))))
      (is (= job-result
             (deref (http/request!! {:endpoint "http://localhost:8080"
                                     :api-key "foo"
                                     :api-secret "bar"}
                                    "deleteSnapshot"
                                    {:id (rand-uuid)})))))))

(deftest async-job-handling
  (let [job-id (rand-uuid)
        job-result {:success true}
        job-resp {:queryasyncjobresultresponse
                  {:cmd "org.apache.cloudstack.api.command.user.snapshot.DeleteSnapshotCmd"
                   :jobid  (str job-id)
                   :jobstatus 1
                   :jobresult job-result}}]
    (utils/with-server 8080 {:deleteSnapshot       (constantly {:status 200
                                                                :body {:deletesnapshotresponse
                                                                       {:jobid job-id}}})
                             :queryAsyncJobResult (constantly {:status 200
                                                               :body job-resp})}
      (is (= job-result
             (deref (http/request!! {:endpoint "http://localhost:8080"
                                     :api-key "foo"
                                     :api-secret "bar"}
                                    "deleteSnapshot"
                                    {:id (rand-uuid)}))
             (deref (http/request!! {:endpoint "http://localhost:8080"
                                     :api-key "foo"
                                     :api-secret "bar"
                                     :throw-on-job-failure? true}
                                    "deleteSnapshot"
                                    {:id (rand-uuid)})))))))
(deftest listing-commands
  (let [template-id (rand-uuid)
        error       (format "Unable to execute API command listtemplates due to invalid value. entity %s does not exist." template-id)
        error-resp  {:listtemplatesresponse
                     {:cserrorcode 9999
                      :errorcode 431
                      :errortext error}}]
    (utils/with-server 8080 {:listVirtualMachines  (constantly {:status 200
                                                                :body {:listvirtualmachinesresponse
                                                                       {:count 0
                                                                        :virtualmachine []}}})

                             :listTemplates (constantly {:status 431
                                                         :body error-resp})}
      (is (= []
             (deref (http/request!! {:endpoint "http://localhost:8080"
                                     :api-key "foo"
                                     :api-secret "bar"}
                                    "listVirtualMachines"
                                    {}))))
      (try
        (deref (http/request!! {:endpoint "http://localhost:8080"
                                :api-key "foo"
                                :api-secret "bar"}
                               "listTemplates"
                               {:id template-id}))
        (is false "call should have failed")
        (catch Exception e
          (let [{:keys [body status]} (ex-data (ex-cause e))]
            (is (= 431 status))
            (is (= error-resp
                   (json/parse-string body true)))))))))

(deftest direct-non-list-response
  (let [payload {:id "yolo" :name "ipool"}]
    (utils/with-server 8080 {:getInstancePool (constantly {:status 200
                                                           :body {:getinstancepoolresponse
                                                                  {:count 1
                                                                   :instancepool [payload]}}})}
      (is (= [payload]
             (deref (http/request!! {:endpoint "http://localhost:8080"
                                     :api-key "foo"
                                     :api-secret "bar"}
                                    "getInstancePool"
                                    payload)))))

    (utils/with-server 8080 {:createInstancePool (constantly {:status 200
                                                              :body {:createinstancepoolresponse
                                                                     {:count 1
                                                                      :instancepool [payload]}}})}
      (is (= [payload]
             (deref (http/request!! {:endpoint "http://localhost:8080"
                                     :api-key "foo"
                                     :api-secret "bar"}
                                    "createInstancePool"
                                    payload)))))))

(deftest body-read-test
  (is (= []
         (http/read-body-with-timeout! (clojure.java.io/input-stream (.getBytes "[]"))
                                       10)))
  (let [closed? (atom false)
        input-stream (proxy [InputStream] []
                       (read ^int
                         ([^bytes bytes _ _]
                          (Thread/sleep 200)
                          "not json"))
                       (close [] (reset! closed? true)))]
    (is (thrown? clojure.lang.ExceptionInfo
                 (http/read-body-with-timeout! input-stream
                                               100)))
    (is @closed? "make sure it was auto-closed")))
