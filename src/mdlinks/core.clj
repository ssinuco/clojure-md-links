(ns mdlinks.core
  (:import [org.commonmark.parser Parser]
           [org.commonmark.node Node Link AbstractVisitor]
           [mdlinks.visitors LinkVisitor])
  (:gen-class))

(use '[clojure.java.io :only (file)])

(defn get-folder-files-map
  [parent]
  (if (.isDirectory parent)     
    (let [children (.listFiles parent)]
      (flatten 
       (map 
        (fn
          [child]
          (get-folder-files-map child))
        children)))
    [parent]))

(defn get-folder-files
  [queue]
  (if (not-empty queue)
    (let [firstItem (first queue)]
      (if (.isDirectory firstItem)        
        (get-folder-files 
         (concat 
          (rest queue) 
          (seq (.listFiles firstItem))))
        (conj 
         (get-folder-files (rest queue)) 
         firstItem)))
    (seq [])))

(defn process-file
  [filePath]
  (let [p (promise)]    
    (future 
      (Thread/sleep 5000)
      (let [parser (.build (Parser/builder))
            visitor (LinkVisitor.)
            content (slurp filePath)]
        (let [parsed (.parse parser content)]
          (.accept parsed visitor)
          (let [fileLinks (.getLinks visitor)]
            (deliver p (into [] (seq fileLinks)))))))
    p))

(defn process-folder
  [folderPath]
  (let [p (promise)]
    (future
      (let [folderSeq (seq [(file folderPath)])]        
        (let [promises 
              (mapv 
               #(process-file %) 
               (get-folder-files folderSeq))]
          (let [links 
                (mapv (fn [p] @p) promises)]
            (deliver p links)))))
    p))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(println (flatten @(process-folder "./data")))
