(ns roterski.neil-tui.core
  (:require [roterski.doorbell :refer [autocomplete]]
            [lambdaisland.regal :as regal]
            [babashka.terminal :refer [tty?]]
            [babashka.fs :as fs]
            [babashka.process :as bp]
            [clojure.string :as str]))

(def neil-line-regex
  (regal/regex
   [:cat
    [:cat ":"
     [:capture [:alt
                "lib" "version" "description"]]]
    [:+ :whitespace]
    [:capture [:alt
               [:cat
                "\""
                [:* [:not "\""]]
                "\""]
               [:+ :non-whitespace]]]]))

(defn neil-line->map
  [line]
  (->> line
       (re-seq neil-line-regex)
       (mapv (fn [[_ k v]]
               [(keyword k) v]))
       (into {})))

(defn ->deps-edn-paths
  []
  (let [!files (atom [])
        root-dir (str (fs/cwd))]
    (fs/walk-file-tree root-dir
                       {:visit-file (fn [f _attrs]
                                      (when (contains? #{"deps.edn" "bb.edn"} (fs/file-name f))
                                        (swap! !files conj (str "." (subs (str f) (count root-dir)))))
                                      :continue)})
    @!files))

(when (tty? :stdout)
  (let [{:keys [lib]} (->> (autocomplete (fn [query]
                                           (when (> (count query) 3)
                                             (let [{:keys [exit out]} @(bp/process {:out :string
                                                                                    :err :string}
                                                                                   (str "neil dep search " query))]
                                               (when (zero? exit)
                                                 (str/split-lines out))))))
                           neil-line->map)
        deps-edn-paths (->deps-edn-paths)
        deps-edn-path (if (= (count deps-edn-paths) 1)
                        (first deps-edn-paths)
                        (autocomplete (fn [query]
                                        (->> deps-edn-paths
                                             (filter #(str/includes? % query))))))]
    (bp/shell (str "neil add dep " lib " --deps-file " deps-edn-path))))
