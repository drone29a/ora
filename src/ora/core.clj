(ns ora.core
  (:require [ora.line :as l]
            [quil.core :as q]
            [clojure.core.async :as a
             :refer [>! <! >!! <!! go chan buffer
                     close! thread alts! alts!! timeout]])
  (:import [org.jtransforms.fft DoubleFFT_1D])
  (:gen-class))

(set! *warn-on-reflection* true)

(defrecord Complex [^double real
                    ^double imag])

(defn signal-val
  [lo-byte
   hi-byte]
  (comment (Float/intBitsToFloat (bit-and 0x0000ffff
                                          (bit-or (bit-and lo-byte 0xff)
                                                  (bit-shift-left (bit-and hi-byte 0xff) 8)))))
  (comment (-> (bit-or (bit-and lo-byte 0xff)
                       (bit-shift-left (bit-and hi-byte 0xff) 8))
               ;;(/ (double Short/MAX_VALUE))
               ;;short
               double))
  ;; Not masking high byte seems to be correct. Seeing wavvy values
  (-> (bit-or (bit-and lo-byte 0xff)
              (bit-shift-left hi-byte 8))
               ;;(/ (double Short/MAX_VALUE))
               ;;short
               double))

(defn run-fft
  [^doubles samples]
  (let [fft (DoubleFFT_1D. (alength samples))
        data (double-array (* (alength samples) 2))]
    (System/arraycopy samples 0 data 0 (alength samples))
    (.realForwardFull fft ^doubles data)
    data))

(defn collect-window
  [window-size
   in
   out]
  (a/go-loop [lo-byte (<! in)
              hi-byte (<! in)
              vals (double-array window-size)
              count 0]
    ;; Collect values for window, run fft, output fft result
    (let [^double val (signal-val lo-byte hi-byte)
          ^doubles vals vals]
      (if (= count window-size)
        (do
          (>! out (aclone vals))
          (aset vals 0 val)
          (recur (<! in)
                 (<! in)
                 vals
                 1))
        (do
          (aset vals count val)
          (recur (<! in)
                 (<! in)
                 vals
                 (inc count)))))))

(defn create-window-chan
  [window-size
   in]
  (let [out (chan (a/sliding-buffer 16834))]
    (collect-window window-size in out)
    out))

(defn find-max-with-index
  [cs]
  (loop [count 0
         rem-cs (rest cs)
         ^Complex max-c (first cs)
         max-idx 0]
    (if (not-empty rem-cs)
      (let [c ^Complex (first rem-cs)]
        (if (> (Math/abs (.imag c))
               (Math/abs (.imag max-c)))
          (recur (inc count) (rest rem-cs) c count)
          (recur (inc count) (rest rem-cs) max-c max-idx)))
      [max-idx max-c])))

(def draw-state (atom {:max-idx 0}))

(defn convert-to-complex
  [^doubles fft-result]
  (let [^"[Lora.core.Complex;" complex-nums (make-array Complex (/ (alength fft-result) 2))]
    (dotimes [idx (/ (alength fft-result) 2)]
      (aset complex-nums idx (Complex. (aget fft-result (* 2 idx))
                                       (aget fft-result (+ (* 2 idx) 1)))))
    complex-nums))

(defn run
  [line-in]
  (let [in (:out line-in)
        window-size 2048
        window-in (create-window-chan window-size in)]
    (a/go-loop [window (<! window-in)]
      (let [^doubles fft-output (run-fft window)
            ;;fft-nums (map (fn [[r i]] (Complex. r i)) (partition 2 fft-output))
            fft-nums (convert-to-complex fft-output)
            [max-idx ^Complex max-item] (find-max-with-index fft-nums)]
        (swap! draw-state assoc :max-idx max-idx)
        (recur (<! window-in))))))

(defn -main
  "Do things."
  [& args]
  (let [line-in (l/start-capture-line-in)]
    (run line-in)))

(defn setup []
  (q/frame-rate 30)
  (q/background 200))

(defn draw []
  (let [max-idx (@draw-state :max-idx)]
    (q/stroke (/ max-idx 255 255 255 10))
    (q/stroke-weight 3)
    (q/fill (/ 255 (max max-idx 1))
            (min 255 (+ 30 (/ 255 (max max-idx 1))))
            (min 255 (* 160 (/ max-idx 100)))
            ;;(max 100 (* 255 (/ 10 (max max-idx 1))))
            70
            )

    (let [diam (+ 50 (* 4 max-idx))
          x    (min (+ 10 (* 5 max-idx) (/ max-idx 280)) (- (q/width) 20))      
          ;;y    (+ (q/random -50 50) (/ (q/height) 2))
          y    (/ (q/height) 2)]
      (q/ellipse x y diam diam))))

(q/defsketch example
  :title "blam"
  :settings #(q/smooth 2)
  :setup setup
  :draw draw
  :size [800 600])
