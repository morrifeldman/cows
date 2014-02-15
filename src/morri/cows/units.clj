(ns morri.cows.units)

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

(def mmHg->torr identity)
(def torr->mmHg identity)

(def degree \u00B0)

(def unit-conversions
  {:celsius {:fahrenheit c->f}
   :fahrenheit {:celsius f->c}
   :hectopascal {:torr hPa->torr
                 :mmHg hPa->mmHg}
   :torr {:hectopascal torr->hPa
          :mmHg torr->mmHg}
   :mmHg {:torr mmHg->torr
          :hectopascal mmHg->hPa}})

(def unit-info
  {:celsius {:symbol (str degree "C")
             :label "Celsius"}
   :fahrenheit {:symbol (str degree "F")
                :label "Fahrenheit"}
   :torr {:symbol "torr"
          :label "Torr"}
   :mmHg {:symbol "mmHg"
          :label "mmHg"}
   :hectopascal {:symbol "hPa"
                 :label "hectopascal"}
   :percent {:symbol "%"
             :label "Percent"}
   :lux {:symbol "lx"
         :label "lux"}})
