(ns exoscale.compute.api.spec
  (:require [clojure.spec.alpha :as spec]))

(spec/def :zone/zoneid           uuid?)
(spec/def :zone/zone             string?)
(spec/def :so/serviceofferingid  uuid?)
(spec/def :so/serviceoffering    string?)
(spec/def :tmpl/templateid       uuid?)
(spec/def :tmpl/template         string?)
(spec/def :sg/securitygroupids   (spec/coll-of uuid?))
(spec/def :sg/securitygroupnames (spec/coll-of string?))
(spec/def :vm/name               string?)
(spec/def :vm/displayname        string?)
(spec/def :vm/group              string?)
(spec/def :vm/ip4                boolean?)
(spec/def :vm/ip6                boolean?)
(spec/def :vm/rootdisksize       pos-int?)
(spec/def :vm/startvm            boolean?)
(spec/def :vm/userdata           string?)
(spec/def :vm/keypair            string?)

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
                      :vm/displayname
                      :vm/group
                      :vm/ip4
                      :vm/ip6
                      :vm/keypair
                      :vm/name
                      :vm/rootdisksize
                      :vm/startvm
                      :vm/userdata]))

(spec/def :exoscale.compute/vm
  (spec/or :uuid uuid?
           :name string?
           :vm   :exoscale.compute/vmdef))

(spec/def :exoscale.compute.api.config/api-key string?)
(spec/def :exoscale.compute.api.config/api-secret string?)
(spec/def :exoscale.compute.api.config/endpoint string?)

(spec/def :exoscale.compute.api.config/client-opts
  (spec/keys :req-un [:exoscale.compute.api.config/api-key
                      :exoscale.compute.api.config/api-secret]
             :opt-un [:exoscale.compute.api.config/endpoint]))
