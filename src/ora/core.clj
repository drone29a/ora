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
(comment
  (defn run-fft
    [^doubles samples]
    (let [fft (DoubleFFT_1D. (alength samples))
          data (double-array (* (alength samples) 2))]
      (System/arraycopy samples 0 data 0 (alength samples))
      (.realForwardFull fft ^doubles data)
      data)))

(defn run-fft
  [^doubles samples]
  (let [fft (DoubleFFT_1D. (alength samples))
        data (double-array (* (alength samples) 1))]
    (System/arraycopy samples 0 data 0 (alength samples))
    (.realForward fft ^doubles data)
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
            fft-nums (convert-to-complex fft-output)
            [max-idx ^Complex max-item] (find-max-with-index fft-nums)
            magnitudes (doall (map (fn [^Complex c] (Math/abs (.imag c))) fft-nums))]
        
        (swap! draw-state assoc
               :max-idx max-idx
               :magnitudes magnitudes)
        (recur (<! window-in))))))

(defn -main
  "Do things."
  [& args]
  (let [line-in (l/start-capture-line-in)]
    (run line-in)))

(defn setup []
  (q/frame-rate 30)
  (q/background 0))

(defn draw-max
  []
  (let [max-idx (@draw-state :max-idx)]
    (q/stroke (min max-idx 255) 255 (/ 255 (max max-idx 1)) 10)
    (q/stroke-weight 3)
    (q/fill (/ 255 (max max-idx 1))
            (min 255 (+ 30 (/ 255 (max max-idx 1))))
            (min 255 (* 160 (/ max-idx 100)))
            70)    

    (let [diam (+ 50 (* 4 max-idx))
          x    (min (+ 10 (* 5 max-idx) (/ max-idx 280)) (- (q/width) 20))      
          ;;y    (+ (q/random -50 50) (/ (q/height) 2))
          y    (/ (q/height) 2)]
      (q/ellipse x y diam diam))))

(defn draw-hist
  []
  (let [magnitudes (doall (@draw-state :magnitudes))]
    (q/stroke 255 255 255 0)
    (q/stroke-weight 2)
    (q/fill 0 0 0 30)
    (q/rect 0 0 (q/width) (q/height))

    (let [num-mags (count magnitudes)]
      (doseq [[idx mag] (map-indexed vector magnitudes)]
        (let [y (/ (* 2.5 mag) (q/height))
              x (* idx (/ (q/width) num-mags))
              diam 10]

          (q/fill (* 255 (/ y (q/height))) 150 30)
          ;;(q/ellipse x y diam diam)
          (q/rect x 0 10 y)
          )))))

(defn draw []
  (draw-hist))

(q/defsketch scratch
  :title "scratch"
  :settings #(q/smooth 2)
  :setup setup
  :draw draw
  :size [800 600])
