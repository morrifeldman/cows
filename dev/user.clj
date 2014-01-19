(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer (javadoc)]
   [clojure.pprint :refer (pprint)]
   [clojure.reflect :refer (reflect)]
   [clojure.repl :refer (apropos dir doc find-doc pst source)]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [morri.cows :as cows]
   [morri.cows.system :as system]
   [morri.cows.devices.bmp085 :as bmp085]))

(def system-state
  "A Var containing an object representing the application under
  development."
  nil)

(defn init
  "Creates and initializes the system under development in the Var
  #'system-state."
  []
  (alter-var-root
   #'system-state
   (constantly (system/system system/default-config))))

(defn start
  "Starts the system running, updates the Var #'system-state."
  []
  (alter-var-root #'system-state system/start))

(defn stop
  "Stops the system if it is currently running, updates the Var
  #'system-state."
  []
  (alter-var-root
   #'system-state
   (fn [s] (when s (system/stop s)))))

(defn go
  "Initializes and starts the system running."
  []
  (init)
  (start)
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after 'user/go))

;; (reset)