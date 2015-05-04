(ns open-arpa.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [pathetic.core :as paths]
            [open-arpa.dictionaries :as dicts :refer [pollutants stations]]
            ))

(def centraline (io/file "resources/centraline.csv"))
(def ASM (io/file "resources/new_layout/ASM/2005/BARI Asm 2005.csv"))
(def Giorgi (io/file "resources/new_layout/Giorgiloro/2006/LECCE Surbo 2006.csv"))
(def path "resources/new_layout")
(def det-path "resources/processed-files")


(defn select-the-nth-row-in-a-csv-file [file index]
  (nth file (- index 1)))

(defn third-rows [files]
  (map select-the-nth-row-in-a-csv-file files (repeat 3)))

(defn fifth-rows [files]
  (map select-the-nth-row-in-a-csv-file files (repeat 5)))


(defn file-order [file-contents pollutants]
  (let [columns (select-the-nth-row-in-a-csv-file file-contents 3)
        ;measure-units (select-the-nth-row-in-a-csv-file file-contents 5)
        indices (range (count columns))]
    (sort-by :index (map (fn [index column]
                           (let [substance (pollutants column)
                                 ]
                             {:substance substance :measurement-unit (dicts/measurement-units substance) :index index})
                           )
                         indices columns))))

(defn file-as-csv [file]
  (list  (.getName file) (csv/read-csv (io/reader file))))

 (defn files-collection [path]
    (filter
     (fn [thing]
       (.isFile thing))
     (file-seq (io/file path))))

(defn file-contents [file]
  (drop 8 (second (file-as-csv file))))

(defn back-to-flat [contents]
  (mapv (fn [el]
          [(:date el) (:substance el) (:measurement el) (:measurement-unit el) (:station el) (:lat el) (:lon el)])
         contents))
 
(defn file-as-maps [order file-contents station]
  (defn recur-through-row
    ([row] (recur-through-row (next row)  (row 0)))
    ([row date] (let [new-order (map (fn [index] (conj index {:date date})) (next order))]
                  (remove nil? (map (fn [index item]
                                         (if (> (count item) 0)
                                           (assoc index :measurement item :station station)))
                                       new-order
                                       row)))))
  
    (map recur-through-row
         file-contents))

(defn extracted-station-name [file-contents]
   (second (select-the-nth-row-in-a-csv-file file-contents 2))) 

(defn new-extracted-station-name [file]
  (let [path (paths/up-dir (paths/up-dir (paths/parse-path file)))
        length (count path)
        index (- length 1)]
    (path index)
  ))

(defn insert-coordinates [file-contents stations]
  (map (fn [item]

         (if-let [coords (stations (item :station))]
           (assoc item
                  :lat (coords 0)
                  :lon (coords 1))
           (assoc item
                  :lat ""
                  :lon ""))
   
         )
       (mapcat (fn [el] el)  file-contents)
  ))

(defn produce-stations [file]
  (let [contents (csv/read-csv (io/reader file))]
    (reduce conj (map (fn [row] {(row 3) [(row 6) (row 7)]})  contents))))

(defn process-file [file pollutants]
  (let [as-csv (file-as-csv file)
        ;name (first as-csv)
        contents (second as-csv)
        station (new-extracted-station-name file)        
        order (file-order contents pollutants)
        purged (drop 7 contents)
        ;; unita di misura
        ]
    (back-to-flat (insert-coordinates (file-as-maps order purged station) (produce-stations centraline)))))

(defn write-file [contents]
  (with-open [out-file (io/writer (str  "resources/processed-files/result.csv" ))]
    (csv/write-csv out-file
                   contents)))

(defn main []
  (write-file (mapcat process-file
                      (files-collection path)
                      (repeatedly (fn [] dicts/pollutants)) ))) 



(defn stations-names []
  (apply sorted-set (map second (map extracted-station-name (files-collection path)))))
  
(defn pollutant-name [file]
  (let [file-contents (second (file-as-csv file))]
    (into [] (rest (select-the-nth-row-in-a-csv-file file-contents 3)))))

(defn pollutants-names []
  (apply sorted-set (mapcat pollutant-name (files-collection path))))



