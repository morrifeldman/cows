(defproject morri/cows "0.1.0-SNAPSHOT"
  :description "What are the cows doing? A Raspberry Pi weather station"
  :url "TODO"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}
  :repositories {"sonatype-oss-public"
                 "https://oss.sonatype.org/content/groups/public/"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.pi4j/pi4j-core "1.0-SNAPSHOT"]
                 [org.clojure/math.numeric-tower "0.0.2"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [compojure "1.1.6"]
                 [clojurewerkz/machine_head "1.0.0-beta6"]
                 [org.clojure/data.json "0.2.4"]]
  :main ^:skip-aot morri.cows
  :target-path "target/%s"
  :repl-options {:timeout 300000}
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [slamhound "1.5.0"]]
                   :source-paths ["dev"]}})
