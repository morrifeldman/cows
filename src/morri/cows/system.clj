(ns morri.cows.system
  (:require
   [morri.cows.units :as units]
   [morri.cows.xively :as xively]
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clojure.pprint :refer [pprint]]
   [compojure.core :refer :all]
   [ring.util.response :as ring-resp]
   [ring.adapter.jetty :as jetty]
   [clojurewerkz.machine-head.client :as mh]
   [clojurewerkz.machine-head.durability :as durable]
   [clojure.string :as str]
   [clj-time.core :as time])
  (:use [hiccup.core]))

(def default-config
  (io/resource "default-config.edn"))

(defn tprn "Transparent prn"
  [x]
  (prn x)
  x)

(defmulti init-device
  "Initialize a device, passing its configuration into the chosen init
  method"
  (fn [device-cfg] (:name device-cfg)))

(defn init
  "Each device gets to take its own configuration and add any keys
  that it wants during initialization"
  [device-cfg-coll]
  (for [device-cfg device-cfg-coll
        :when (:active device-cfg)]
    (init-device device-cfg)))

(defn add-timestamp [device-report]
  (assoc device-report :at (str (time/now))))

(defn add-unit-info
  "Add information about the unit in the right format for xively"
  [{:keys [unit] :as device-report}]
  (if-let [unit-info (unit units/unit-info)]
    (assoc device-report :unit unit-info)
    (throw (Exception. (str "Unit " unit " not defined")))))

(defn switch-unit
  "If the current unit can be converted to a preferred unit then do it"
  [preferred-units
   {current-unit :unit :as device-report}]
  (if-let [new-unit (some preferred-units
                          (keys (current-unit units/unit-conversions)))]
    (-> device-report
        (update-in [:current_value] (new-unit
                                     (current-unit units/unit-conversions)))
        (assoc :unit new-unit))
    device-report))

(defn format-current-value
  "Format the values according to the format string"
  [number-format device-report]
  (update-in device-report [:current_value]
             #(read-string (format number-format %))))

(defmulti read-device
  "Read a device given it's configuration map"
  (fn [device-cfg] (:name device-cfg)))

(defn read-weather
  "Read the weather from all the devices.  The read-device function
  for each device should accept the configuration map for the device
  and should return a map with keys :current_value and :unit"
  [{:keys [devices
           preferred-units
           number-format]}]
  (let [data-fixer (comp
                    add-timestamp
                    add-unit-info
                    (partial format-current-value number-format)
                    (partial switch-unit preferred-units))]
    (map data-fixer
         (apply concat
                (for [device-cfg devices
                      :when (:active device-cfg)]
                  (read-device device-cfg))))))

(defn connect-xively [{:keys [running?
                              xively
                              mqtt-id
                              mqtt-persister]
                       :as agent-state}]
  (when @running?
    (reset! xively (xively/connect-xively
                    mqtt-id
                    mqtt-persister
                    (System/getenv "XIVELY_API_KEY")))
    agent-state))

(defn log-weather
  "Log the weather to Xively.  If connect or publish fails back off
  geometrically starting at 5 secs and doubling the wait time after
  each failure."
  [{:keys [running? xively feed-id] :as log-state} current-weather]
  (loop [next-publish-interval 5000]
    (when @running?
      ;; (println "logging @" (time/now))
      (when-not
          (try
            (when-not (mh/connected? @xively)
              (println "Xively connection lost. Trying to reconnenct.")
              (connect-xively log-state)
              (println "Reconnected"))
            (xively/publish-feed
             @xively
             feed-id
             {:version "1.0.0"
              :datastreams (vec current-weather)})
            true                        ;we don't have an error, return true
            (catch Exception e false))  ;we did have an error, return false
        (println "Waiting" next-publish-interval "ms for network")
        (Thread/sleep next-publish-interval)
        (recur (* 2 next-publish-interval)))))
  log-state)

(defn read-log-weather
  "Read and log the weather.  Since these are agents we can send the
  next actions off while we are still reading the current weather."
  [{:keys [running?
           read-log-interval] :as weather-state}
   {:keys [logging-agent] :as system-state}]
  (if @running?
    (let [current-weather (read-weather weather-state)]
      (send-off logging-agent log-weather current-weather)
      (send-off *agent* read-log-weather system-state)
      (Thread/sleep read-log-interval)
      ;; (println "Reading Weather")
      (assoc weather-state :current-weather current-weather))
    weather-state))

(defn format-weather
  "Format the weather for a basic web page display"
  [number-format {:keys [id current_value unit]}]
  (format (str "%s: " number-format " %s")
          (name id)
          current_value
          (:symbol unit)))

(defn weather-handler
  "Ring handler for to display the current weather and the links for
  the main cows page"
  [{:keys [reading-agent logging-agent]}]
  (html
   [:head [:title "Cows"]]
   [:body
    [:h3 "What are the cows doing?"]
    [:p (interpose [:br]
                   (for [report (:current-weather @reading-agent)]
                     (format-weather (:number-format @reading-agent) report)))]
    [:p
     [:a
      {:href (str "https://xively.com/feeds/" (:feed-id @logging-agent))}
      "Xively Feed"]
     [:br]
     [:a {:href "/system"} "System State"]]]))

(defn create-system
  "Create the system based on a config file"
  [config-file]
  (let [config (edn/read-string (slurp config-file))]
    (-> config
        (assoc :reading-agent (agent
                               {:running? (atom true)
                                :read-log-interval (:read-log-interval config)
                                :preferred-units (:preferred-units config)
                                :number-format (:number-format config)
                                :devices (init (:device-cfg config))})
               :logging-agent (agent {:running? (atom (:log-weather? config))
                                      :feed-id (:feed-id config)})
               :jetty-server (atom {:running? (:serve-weather? config)
                                    :jetty-port (:jetty-port config)
                                    :jetty-server nil}))
        (dissoc :read-log-interval
                :preferred-units
                :number-format
                :device-cfg
                :log-weather?
                :feed-id
                :serve-weather?
                :jetty-port))))

(defn make-cow-routes
  "Make the routes durring system startup so that we can pass the
  system state to the weather handler"
  [system-state]
  (routes
   (GET "/system" [] (html [:pre (h (with-out-str (pprint system-state)))]))
   (GET "/" [] (weather-handler system-state))))

;; based on http://stuartsierra.com/2010/01/08/agents-of-swing

(defn start-logging
  "Start the xively agent"
  [{:keys [running?] :as agent-state}]
  (when @running?
    (send-off *agent* connect-xively)
    (assoc agent-state
      :mqtt-id (mh/generate-id)
      :mqtt-persister (durable/new-memory-persister)
      :xively (atom nil))))

(defn start-serving
  "Start the jetty server"
  [{:keys [running? jetty-port] :as jetty-server} cow-routes]
  (when running?
    (assoc jetty-server :jetty-server
           (jetty/run-jetty cow-routes
                            {:join? false
                             :port jetty-port}))))

(defn start-system
  "Start the system up.  Start the jetty server, start the logging and
  reading agents"
 [{:keys [jetty-server
          reading-agent
          logging-agent] :as system-state}]
  (swap! jetty-server start-serving (make-cow-routes system-state))
  (send-off logging-agent start-logging)
  (send-off reading-agent read-log-weather system-state)
  system-state)

(defn stop-an-agent
  "Stop an agent and display any agent errors"
  [ag]
  (when-let [err (agent-error ag)]
    (println "Agent:")
    (prn @ag)
    (println "Failed with:")
    (println err)
    (restart-agent ag @ag :clear-actions true))
  (reset! (:running? @ag) false))

(defn stop-system
  "Stop the system.  Especially the jetty server, which if it isn't
  stopped requires a restart of the repl"
  [system-state]
  (when-let [jetty-server (:jetty-server @(:jetty-server system-state))]
    (.stop jetty-server))
  (swap! (:jetty-server system-state) #(assoc % :running? false))
  (when-let [xively @(:xively @(:logging-agent system-state))]
    (try (mh/disconnect xively)
         (catch Exception e (println e))))
  (doseq [agent-key [:logging-agent :reading-agent]]
    (stop-an-agent (agent-key system-state)))
  system-state)
