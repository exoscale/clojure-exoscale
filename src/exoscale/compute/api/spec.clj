(ns exoscale.compute.api.spec
  (:require [exoscale.compute.api.http :as http]
            [clojure.spec.alpha :as spec]))

(spec/def ::ne-string
  (spec/and string? (complement clojure.string/blank?)))

(def re-url?
  (partial re-matches #"(?i)^http(s?)://.*"))

(spec/def ::url
  (spec/and
   ::ne-string
   re-url?))

(spec/def :zone/zoneid           uuid?)
(spec/def :zone/zone             string?)
(spec/def :so/serviceofferingid  uuid?)
(spec/def :so/serviceoffering    string?)
(spec/def :tmpl/templateid       uuid?)
(spec/def :tmpl/template         string?)
(spec/def :sg/securitygroupids   (spec/coll-of uuid?))
(spec/def :sg/securitygroupnames (spec/coll-of string?))
(spec/def :ag/affinitygroupids   (spec/coll-of uuid?))
(spec/def :ag/affinitygroupnames (spec/coll-of string?))
(spec/def :vm/name               string?)
(spec/def :vm/displayname        string?)
(spec/def :vm/group              string?)
(spec/def :vm/ip4                boolean?)
(spec/def :vm/ip6                boolean?)
(spec/def :vm/rootdisksize       pos-int?)
(spec/def :vm/startvm            boolean?)
(spec/def :vm/userdata           string?)
(spec/def :vm/keypair            string?)
(spec/def :vm/networkids         (spec/coll-of uuid?))

(spec/def :vm/forced?            boolean?)

(spec/def :exoscale.compute/vmparams
  (spec/or :map (spec/keys :opt-un [:vm/forced? :vm/displayname
                                    :vm/userdata :vm/group :vm/name
                                    :vm/securitygroupids])
           :nil nil?))

(spec/def :exoscale.compute/vmdef
  (spec/keys :req-un [(or :so/serviceofferingid :so/serviceoffering)
                      (or :tmpl/templateid :tmpl/template)
                      (or :zone/zoneid :zone/zone)]
             :opt-un [:sg/securitygroupids
                      :sg/securitygroupnames
                      :ag/affinitygroupids
                      :ag/affinitygroupnames
                      :vm/displayname
                      :vm/group
                      :vm/ip4
                      :vm/ip6
                      :vm/keypair
                      :vm/name
                      :vm/rootdisksize
                      :vm/startvm
                      :vm/userdata
                      :vm/networkids]))

(spec/def :exoscale.compute/vm
  (spec/or :uuid uuid?
           :name string?
           :vm   :exoscale.compute/vmdef))

(spec/def :exoscale.compute.api.config/api-key ::ne-string)
(spec/def :exoscale.compute.api.config/api-secret ::ne-string)
(spec/def :exoscale.compute.api.config/endpoint ::url)
(spec/def :exoscale.compute.api.config/max-polls int?)
(spec/def :exoscale.compute.api.config/poll-interval #(>= % http/default-poll-interval))
(spec/def :exoscale.compute.api.config/https-opts map?)

(spec/def :exoscale.compute.api.config/client-opts
  (spec/keys :req-un [:exoscale.compute.api.config/api-key
                      :exoscale.compute.api.config/api-secret]
             :opt-un [:exoscale.compute.api.config/endpoint
                      :exoscale.compute.api.config/max-polls
                      :exoscale.compute.api.config/poll-interval
                      :exoscale.compute.api.config/https-opts]))
