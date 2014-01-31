(ns morri.cows.conversions)

(defn c->f [c]
  (+ (* c (/ (- 212.0 32) 100)) 32))
;; (c->f 100)
;; (c->f 0)

(defn f->c [f]
  (* (- f 32) (/ 100 (- 212.0 32))))
;; (f->c 32)
;; (f->c 212)

(defn hPa->torr [hPa]
  (* (/ 760 1013.25) hPa))
(def hPa->mmHg hPa->torr)

;; (hPa->torr 1013.25)

(defn torr->hPa [torr]
  (* (/ 1013.25 760) torr))
(def mmHg->hPa torr->hPa)

;; (torr->hPa 760)

(def celsius-symbol (str \u00B0 "C"))

(def conversions
  {:Celsius {:Celsius
             {:conv-fn identity
              :unit-symbol celsius-symbol}
             :fahrenheit
             {:conv-fn c->f
              :unit-symbol "F"}}
   :fahrenheit {:fahrenheit
                {:conv-fn identity
                 :unit-symbol "F"}
                :Celsius
                {:conv-fn f->c
                 :unit-symbol celsius-symbol}}
   :hectopascal {:hectopascal
                 {:conv-fn identity
                  :unit-symbol "hPa"}
                 :torr
                 {:conv-fn hPa->torr
                  :unit-symbol "torr"}}
   :torr {:torr
          {:conv-fn identity
           :unit-symbol "torr"}
          :hectopascal
          {:conv-fn torr->hPa
           :unit-symbol "hPa"}}
   :lux {:lux
         {:conv-fn identity
          :unit-symbol "lx"}}
   :percent {:percent
             {:conv-fn identity
              :unit-symbol "%"}}})
