(ns exoscale.compute.ssh
  "Basic remote SSH exec support, expects a provided
   RSA private key. No checking of known hosts"
  (:require [exoscale.compute.api.keypair :as keypair])
  (:import com.jcraft.jsch.JSch))

(defn ^{:no-doc true} add-identity
  "Add identity to a JSch instance. Expects a string containing
   an RSA private key"
  [jsch k]
  (let [privkey (if (keyword? k) (keypair/private-key k) k)]
    (.addIdentity jsch nil (.getBytes (str privkey)) nil nil)))

(defmacro with-session
  [sym [jsch host user port] & body]
  `(let [~sym (doto (.getSession ~jsch ~user ~host ~port)
                (.setConfig "StrictHostKeyChecking" "no")
                (.connect (int 2000)))]
     (try
       ~@body
       (finally (.disconnect ~sym)))))

(defn exec
  [keypair host cmd {:keys [user port]}]
  (let [jsch (JSch.)]
    (add-identity jsch keypair)
    (with-session session [jsch host (or user "root") (or port 22)]
      (let [chan (.openChannel session "exec")]
        (try
          (.setCommand chan cmd)
          (.setInputStream chan nil)
          (.setErrStream chan nil)
          (let [output (.getInputStream chan)]
            (.connect chan)
            (slurp output))
          (finally (.disconnect chan)))))))

(defn ensure-up
  [keypair host timeout params]
  (let [max-time (+  (System/currentTimeMillis) (* timeout 1000))]
    (loop []
      (when (> (System/currentTimeMillis) max-time)
        (throw (ex-info "failed to connect to host in due time" {})))
      (let [result (try (exec keypair host "echo -n hello" params)
                        (catch Exception _))]
        (when (not= result "hello")
            (Thread/sleep 1000)
            (recur))))
    :connected))

(comment
  (keypair/store-private-key {:name "auto" :privatekey (slurp "/home/pyr/auto.key")})
  ;;  (ssh-exec :auto "some-ip" {:cmd "ls /"})

  (exec :auto "185.150.8.15" "echo -n hello" {})

  (ensure-up :auto "185.150.8.15" 30)

  )
