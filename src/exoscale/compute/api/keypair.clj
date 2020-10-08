(ns exoscale.compute.api.keypair
  "Higher order functions to manipulate keypairs.
   In addition to standard facilities, this also
   bundles an opt-in atom-backed store for private keys
   created here."
  (:refer-clojure :exclude [list])
  (:require [exoscale.compute.api.client :as client]
            [exoscale.compute.api.meta   :as meta]
            [qbits.auspex                :as auspex]))

(defonce keystore
  (atom {}))

(defn sanitize
  [kp]
  (meta/describe kp :exoscale.compute/keypair kp))

(defn by-name
  "The infamous name resolver, using listVirtualMachines since
   Cloudstack does not allow singular get calls"
  [config kp]
  (auspex/chain (client/api-call config :list-ssh-key-pairs {:name (name kp)})
                (fn [resp]
                  (when-not (= 1 (count resp))
                    (throw (IllegalArgumentException.
                            "cannot resolve service offering")))
                  (sanitize
                   (first resp)))))

(defn list
  "List virtual machines"
  [config]
  (auspex/chain (client/api-call config :list-ssh-key-pairs)
                #(mapv sanitize %)))

(defn register
  [config kpname public-key]
  (auspex/chain   (client/api-call config "registerSSHKeyPair"
                                   {:name      (name kpname)
                                    :publickey public-key})
                  :keypair
                  sanitize))

(defn create
  [config kpname]
  (auspex/chain (client/api-call config "createSSHKeyPair" {:name (name kpname)})
                :keypair
                sanitize))

(defn flush-private-keys
  []
  (reset! keystore {}))

(defn store-private-key
  [resp]
  (swap! keystore assoc (:name resp) (:privatekey resp))
  resp)

(defn create-and-store
  [config kpname]
  (auspex/chain (create config kpname) store-private-key))

(defn private-key
  [kpname]
  (get @keystore (name kpname)))

(defn resolve-id
  [target]
  (cond
    (string? target)  {:name target}
    (keyword? target) {:name (name target)}
    (map? target)     target
    :else             (throw (IllegalArgumentException.
                              "cannot coerce to keypair"))))

(defn delete
  [config target]
  (client/api-call config :delete-ssh-key-pair (resolve-id target)))

(comment

  @(create-and-store config :auto)
  @(list config)
  @(delete :auto)

)
