(ns exoscale.compute.api.vm
  "Higher order functions to manipulate VMs"
  (:refer-clojure :exclude [update list])
  (:require [exoscale.compute.api.client   :as client]
            [exoscale.compute.api.zone     :as zone]
            [exoscale.compute.api.offering :as offering]
            [exoscale.compute.api.template :as template]
            [exoscale.compute.api.keypair  :as keypair]
            [exoscale.compute.api.meta     :as meta]
            [exoscale.compute.ssh          :as ssh]
            [manifold.deferred             :as d]
            [clojure.string                :as str]
            [clojure.spec.alpha            :as spec]
            [exoscale.compute.api.spec]))

(defn sanitize-sg
  "Keep relevant information for security groups"
  [{:keys [id name]}]
  {:id   (client/parse-uuid id)
   :name name})

(defn sanitize-template
  "Rewrite template information to contain UUIDs"
  [{:keys [templatename templateid]}]
  {:id   (client/parse-uuid templateid)
   :name templatename})

(defn sanitize-nic
  "Keep relevant information for public NICs.
   We do not keep network IDs and name since this isn't
   actionable information."
  [{:keys [macaddress ipaddress ip6address] :as nic}]
  (cond-> {:macaddress macaddress}
    (some? ipaddress) (assoc :ipv4 {:ip      (:ipaddress nic)
                                    :gateway (:gateway nic)
                                    :netmask (:netmask nic)})
    (some? ip6address) (assoc :ipv6 {:ip   (:ip6address nic)
                                     :cidr (:ip6cidr nic)})))

(defn sanitize-nics
  "Keep relevant information for private network NICs"
  [nics]
  (vec
   (for [nic nics :when (= "Isolated" (:type nic))]
     {:network    {:id   (client/parse-uuid (:networkid nic))
                   :name (:networkname nic)}
      :macaddress (:macaddress nic)
      :id         (client/parse-uuid (:id nic))})))

(defn sanitize-public
  "Filter out public NIC and show relevant information"
  [nics]
  (let [nic (first (filter #(= "Shared" (:type %)) nics))]
    (sanitize-nic nic)))

(defn sanitize-ag
  "Parse affinitygroup UUID"
  [group]
  (update-in group [:id] client/parse-uuid))

(defn sanitize-vm
  "Coerce API response into something a bit more useful"
  [resp]
  (let [tag-acc #(assoc %1 (keyword (:key %2)) (:value %2))]
    (-> (select-keys resp [:id :name :displayname :keypair :memory
                           :cpunumber :group :password])
        (assoc :security-groups (mapv sanitize-sg (:securitygroup resp)))
        (assoc :template (sanitize-template resp))
        (assoc :zone (:zonename resp))
        (assoc :service-offering (-> resp :serviceofferingname str/lower-case keyword))
        (assoc :state (-> resp :state str/lower-case keyword))
        (assoc :tags (reduce tag-acc {} (:tags resp)))
        (update-in [:id] client/parse-uuid)
        (assoc :affinity-groups (mapv sanitize-ag (:affinitygroup resp)))
        (assoc :public (sanitize-public (:nic resp)))
        (assoc :nics (sanitize-nics (:nic resp)))
        (meta/describe :exsocale.compute/vm resp))))



(defn by-name
  "The infamous name resolver, using listVirtualMachines since
   Cloudstack does not allow singular get calls"
  [config vm]
  (d/chain (client/api-call config :list-virtual-machines {:name (name vm)})
           (fn [resp]
             (when-not (= 1 (count resp))
               (throw (IllegalArgumentException.
                       "cannot resolve virtual machine name")))
             (sanitize-vm (first resp)))))

(defn vmdef?
  "Predicate to test against a valid VM definition"
  [x]
  (= :exoscale.compute/vm (meta/type x)))

(defn resolve-id
  "Yield a target VM id from a target definition (name, ID, or vm return)"
  [config target]
  (cond
    (uuid? target)    (str target)
    (keyword? target) (d/chain (by-name config target) :id str)
    (string? target)  (d/chain (by-name config target) :id str)
    (vmdef? target)   (str (:id target))
    :else             (throw (IllegalArgumentException.
                              "invalid argument to resolve-id"))))


(defn ^{:no-doc true} target-action
  [action config target params]
  (let [opcode  (str (name action) "VirtualMachine")
        call-fn #(client/api-call config opcode (merge params {:id %}))]
    (d/chain (resolve-id config target)
             call-fn
             sanitize-vm)))

(defmacro ^{:no-doc true} defvmaction
  "Generate a function to act on a VM"
  [sym & [action]]
  `(defn ~sym
     ~(format "%s virtual machine." (str/capitalize (name sym)))
     [~'config ~'target & [~'params]]
     {:pre [(spec/valid? :exoscale.compute/vmparams ~'params)]}
     (target-action ~(or action (str sym)) ~'config ~'target ~'params)))

(defvmaction start)
(defvmaction stop)
(defvmaction reboot)
(defvmaction update)
(defvmaction reset-password "resetPasswordFor")

(defn get-password
  "Fetch encrypted password"
  [config target]
  (d/chain (resolve-id config target)
           #(client/api-call config :get-vm-password {:id %})
           :password
           :encryptedpassword))

(defn list
  "List virtual machines"
  [config & [params]]
  (d/chain (client/api-call config :list-virtual-machines params)
           (fn [vms] (mapv sanitize-vm vms))))

(defn resolve-indirect-params
  ""
  [config {:keys [zoneid zone serviceoffering template]}]
  (d/let-flow [zid (or zoneid (d/chain (zone/by-name config zone) :id))
               sid (when (some? serviceoffering)
                     (d/chain (offering/by-name config serviceoffering) :id))
               tid (when (some? template)
                     (d/chain (template/by-zone-name config zid template) :id))]
    (cond-> {}
      (some? zone) (assoc :zoneid zid)
      (some? serviceoffering) (assoc :serviceofferingid sid)
      (some? template) (assoc :templateid tid))))

(defn deploy
  "Deploy virtual machine"
  [config params]
  {:pre [(spec/valid? :exoscale.compute/vm params)]}
  (d/chain (resolve-indirect-params config params)
           (fn [resolved] (merge resolved (dissoc params :zone :template :serviceoffering)))
           (fn [params] (client/api-call config :deploy-virtual-machine params))
           sanitize-vm))

(defn destroy
  "Destroy virtual machine"
  [config target]
  (d/chain (resolve-id config target)
           #(client/api-call config :destroy-virtual-machine {:id %})
           (constantly nil)))

(defn ssh
  "Asynchronously reach out to a machine to execute an ssh command"
  [config target command]
  (d/let-flow [vm (if (vmdef? target) target (by-name config target))
               template (template/by-zone-id config
                                             (meta/zone vm)
                                             (get-in vm [:template :id]))]
    (let [pkey (keypair/private-key (:keypair vm))
          user (or (get-in template [:details :username]) "root")
          host (get-in vm [:public :ipv4 :ip])]
      (when (nil? pkey)
        (throw (ex-info (str "cannot find private key named: " (:keypair vm))
                        {:vm vm})))
      (when (nil? host)
        (throw (ex-info "cannot find host address" {:vm vm})))
      (d/future
        (ssh/exec pkey host command {:user user})))))

(defn ensure-up
  "Wait for machine to be up by probbing the SSH port. This assumes that
   inbound SSH is authorized"
  [config target timeout]
  (d/let-flow [vm       (if (vmdef? target) target (by-name config target))
               template (template/by-zone-id config
                                             (meta/zone vm)
                                             (get-in vm [:template :id]))]
    (let [pkey (keypair/private-key (:keypair vm))
          user (or (get-in template [:details :username]) "root")
          host (get-in vm [:public :ipv4 :ip])]
      (when (nil? pkey)
        (throw (ex-info (str "cannot find private key named: " (:keypair vm))
                        {:vm vm})))
      (when (nil? host)
        (throw (ex-info "cannot find host address" {:vm vm})))
      (d/chain
       (d/future (ssh/ensure-up pkey host timeout {:user user}))
       (constantly vm)))))




(comment

  @(list config)

  (do
    @(keypair/create-and-store config :auto)
    @(d/chain  (deploy config
                       {:name            "clojure01"
                        :displayname     "Clojure Test Box"
                        :zone            "at-vie-1"
                        :template        "Linux Debian 9 64-bit"
                        :keypair         "auto"
                        :serviceoffering "micro"})
               #(ensure-up config % 40)
               #(ssh config % "echo hello")))
  @(ensure-up :clojure01)
  @(ssh config :clojure01 "echo hello")



  (keypair/private-key :auto)
  @(by-name config :clojure01)

  @(ssh config :clojure01 "echo hello")
  @(stop config :clojure01)
  
  @(destroy config :clojure01)
  )
