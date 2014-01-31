(ns morri.cows.binary-utils)

(defn byte->ubyte [b]
  (bit-and 0xff b))

(defn short->ushort [s]
  (bit-and s 0xffff)); promotes to int and masks off bits
; (short->ushort -1) ; => 65535

(defn msb+lsb [msb lsb]
  (bit-or
   (bit-shift-left msb 8)
   lsb))

(defn msb+lsb+xlsb [msb lsb xlsb]
  (bit-or
   (bit-shift-left msb 16)
   (bit-shift-left lsb 8)
   xlsb))
