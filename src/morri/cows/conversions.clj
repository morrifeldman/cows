(ns morri.cows.conversions)

(defn c->f [c]
  (+ (* c (/ (- 212.0 32) 100)) 32))

(defn hPa->mmHg [hPa]
  (* (/ 760 1013.25) hPa))
