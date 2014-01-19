(ns morri.cows.system
  (:require [morri.cows :as cows]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [ring.adapter.jetty :as jetty]))

(def default-config
  (io/resource "default-config.edn"))

(defn system [config-file]
  (let [config (edn/read-string (slurp config-file))]
    {:read-weather (agent
                    {:running true
                     :read-log-interval (:read-log-interval config)})
     :log-weather (agent {:running (:log-weather? config)})
     :serve-weather? (:serve-weather? config)
     :server (atom nil)
     :devices (cows/init-devices config)}))

(defn make-handler [system]
  (fn [req]
    (cows/handler system req)))

;; based on http://stuartsierra.com/2010/01/08/agents-of-swing

(defn start [system]
  (send-off (:read-weather system) cows/read-log-weather system)
  (when (:serve-weather? system)
    (reset! (:server system)
            (jetty/run-jetty (make-handler system) {:join? false})))
  system)

(defn stop-agent [state]
  (assoc state :running false))

(defn stop [system]
  (doseq [agent-key [:read-weather :log-weather]]
    (send-off (agent-key system) stop-agent))
  (when-let [server @(:server system)]
    (.stop server))
  system)
