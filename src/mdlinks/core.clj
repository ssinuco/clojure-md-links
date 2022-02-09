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

(defn get-folder-files-seq
  [queue]
  (if (not-empty queue)
    (let [firstItem (first queue)]
      (if (.isDirectory firstItem)        
        (get-folder-files-seq (concat  (rest queue) (seq (.listFiles firstItem))))
        (conj (get-folder-files-seq (rest queue)) firstItem)))
    (seq [])))

(map 
 (fn 
   [aFile] 
   (println (.getAbsolutePath  aFile)))
 (get-folder-files-seq (seq [(file  "./data")])))

(map 
 (fn 
   [aFile] 
   (println (.getAbsolutePath aFile)))
 (get-folder-files-map (file "./data/README-1.md")))

(def links (atom []))

(defn processFile
  [filePath]
  (future 
    (let [parser (.build (Parser/builder))
          visitor (LinkVisitor.)
          content (slurp filePath)]
      (let [parsed (.parse parser content)]
        (.accept parsed visitor)
        (let [fileLinks (.getLinks visitor)]
          (swap! links
                 #(into [] (concat % fileLinks )))
          (into [] (seq fileLinks)))))))

(let [parser (.build (Parser/builder))
      visitor (LinkVisitor.)
      content (slurp "./data/README-1.md")]
  (let [parsed (.parse parser content)]
    (.accept parsed visitor)
    (.getLinks visitor)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(doseq [filePath 
        (get-folder-files-seq (seq [(file  "./data")]))]
  (processFile filePath))
