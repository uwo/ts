#!/usr/bin/env bb
(ns get-vim-colorscheme
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.java.shell :refer [sh]]
            [clojure.data.xml :as xml]
            [clojure.data.zip.xml :refer [xml-> xml1->] :as xmlz]
            [clojure.zip :as zip]))

(def plugin-dir
  (str (fs/expand-home "~/.local/share/nvim/plugged/")))

(defn get-all-plug-colors
  "Return all colors that have been installed manually."
  #_(clojure.pprint/pprint (get-all-plug-colors plugin-dir))
  ;; fd "\bcolors\b" ~/.local/share/nvim/plugged/
  [plugin-dir]
  (let [installed-color-dirs
        (-> (sh "fd" "\\bcolors\\b" plugin-dir)
            :out
            (str/split #"\n"))]
    (->> installed-color-dirs
         (mapcat fs/list-dir)
         (map fs/file-name)
         (map (fn [f]
                (let [[n _] (fs/split-ext f)]
                  n))))))

(def plist-filepath
  "Location of presets"
  (fs/file (fs/expand-home "~/.dotfiles/com.googlecode.iterm2.plist")))

(defn plist->presets
  #_(plist->presets plist-filepath)
  [filepath]
  (with-open [stream (java.io.FileInputStream. filepath)]
    (->> (xml/parse stream)
         :content
         (filter (comp #{:dict} :tag))
         (mapcat :content)
         (drop-while #(not= (:content %) ["Custom Color Presets"]))
         (remove string?)
         (take 2)
         second
         clojure.zip/xml-zip
         ((fn [z] (xml-> z :key)))
         (map xmlz/text ))))

(let [preset-options (set (plist->presets plist-filepath))]
  (s/def ::iterm2-name preset-options))

(let [plug-colors (set (get-all-plug-colors plugin-dir))]
  (s/def ::vim-name plug-colors))

(s/def ::lookup-entry
    (s/keys :req-un [::cli-name ::iterm2-name ::vim-name]))

(defn build-lookup
  #_(clojure.pprint/pprint (build-lookup))
  []
  (->>
    ;; [cli-name iterm2-name vim-name]
    [["dawnfox" "dawnfox_iterm" "dawnfox"]
     ["nord" "nord" "nord"]
     ["terafox" "terafox" "terafox"]
     ["gruvbox-light" "gruvbox-light" "gruvbox"]
     ["gruvbox" "gruvbox-dark" "gruvbox"]
     ["everforest" "Everforest_hard_dark" "everforest"]
     ["everforest-hard-dark" "Everforest_hard_dark" "everforest"]
     ["everforest-hard-light" "Everforest_hard_light" "everforest"]
     ["everforest-medium-dark" "Everforest_medium_dark" "everforest"]
     ["everforest-medium-light" "Everforest_medium_light" "everforest"]
     ["everforest-soft-dark" "Everforest_soft_dark" "everforest"]
     ["everforest-soft-light" "Everforest_soft_light" "everforest"]
     ["nightfox" "nightfox_iterm" "nightfox"]
     ["dayfox" "dayfox_iterm" "dayfox"]
     ["solarized" "Solarized Light" "solarized8_flat"]
     ["duskfox" "duskfox_iterm" "duskfox"]
     ["solarized-dark" "Solarized Dark" "solarized8_high"]
     ["nordfox" "nordfox_iterm" "nordfox"]]
    (reduce (fn [acc [cli-name iterm2-name vim-name]]
              (let [m {:cli-name cli-name
                       :iterm2-name iterm2-name
                       :vim-name vim-name}]
                (if (s/valid? ::lookup-entry m)
                  (assoc acc cli-name m)
                  (let [msg "Lookup entry doesn't conform"
                        data (s/explain-data ::lookup-entry m)]
                    (clojure.pprint/pprint {:ex-info-data data})
                    (throw (ex-info msg data))))))
            {})))

(defn write-color-lookup-file
  []
  (let [lookup (build-lookup)
        lookup-str (with-out-str (clojure.pprint/pprint lookup))]
    (spit "color-lookup.edn" lookup-str)))
