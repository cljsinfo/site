(ns sitegen.news
  (:require
    [clojure.string :as string]
    [util.io :as io]
    [util.markdown :as markdown]
    [util.hiccup :as hiccup]
    [sitegen.layout :refer [common-layout]]
    [sitegen.urls :as urls]
    [goog.string])
  (:import
    goog.i18n.DateTimeFormat))

;;---------------------------------------------------------------
;; Post Retrieval
;;---------------------------------------------------------------

(def posts [])
(def index-filename "news/index.edn")
(def md-dir "news/")

(defn transform-jira [md-text]
  (string/replace
    md-text
    #"\bCLJS-\d+\b"
    #(str "[" % "](http://dev.clojure.org/jira/browse/" % ")")))

(defn add-body
  [{:keys [version] :as post}]
  (let [md-body (->> (str md-dir version ".md")
                     (io/slurp)
                     (transform-jira))
        html-body (markdown/render md-body)]
    (assoc post
      :md-body md-body
      :html-body html-body)))

(defn add-date
  [{:keys [date] :as post}]
  (let [[_ y m d] (re-find #"^(\d\d\d\d)-(\d\d)-(\d\d)" date)
        y (js/parseInt y)
        m (js/parseInt m)
        d (js/parseInt d)
        date (js/Date. y (dec m) d)]
    (assoc post :date date)))

(defn add-url
  [{:keys [version] :as post}]
  (let [url (urls/news-post version)]
    (assoc post :url url)))

(defn add-title
  [{:keys [version title] :as post}]
  (assoc post :title
    (if title
      (str version " - " title)
      version)))

(def transform-post
  (comp add-body
        add-date
        add-url
        add-title))

(defn update! []
  (->> (io/slurp-edn index-filename)
       (map transform-post)
       (set! posts)))

;;---------------------------------------------------------------
;; Page Rendering
;;---------------------------------------------------------------

;; Internationalized Dates
;; http://www.closurecheatsheet.com/i18n#goog-i18n-datetimeformat
;; http://google.github.io/closure-library/api/enum_goog_i18n_DateTimeFormat_Format.html
(def date-format (DateTimeFormat. DateTimeFormat.Format.MEDIUM_DATE))
(defn date-str [date]
  (.format date-format date))

(defn index-page []
  [:div
    [:h3 "Release News"]
    [:p "This is an updated index of the release posts on the "
        [:a {:href "https://groups.google.com/forum/#!forum/clojurescript"}
          "ClojureScript mailing list"] "."
        [:br]
        "You can subscribe to the " [:a {:href "feed.xml"} "RSS feed"] "."]
    [:table
      (for [post (reverse posts)]
        [:tr
          [:td (date-str (:date post))]
          [:td [:a {:href (urls/pretty (:url post))} (:title post)]]])]])

(defn post-meta
  [{:keys [version title date author google-group-msg github-pre-release]}]
  (let [elements
        [(date-str date)
         (when author
           (str "by " author))
         (when google-group-msg
           [:a {:href (str "https://groups.google.com/d/msg/" google-group-msg)}
            "on Google Groups"])
         (when github-pre-release
           [:a {:href (str "https://github.com/clojure/clojurescript/releases/tag/r" version)}
            "on GitHub"])]]
    (->> elements
         (filter identity)
         (interpose " "))))

(defn post-page
  [{:keys [title version html-body] :as post}]
  [:div
    [:h1 title]
    [:p (post-meta post)]
    [:article html-body]])

(defn rss-date [date]
  (.toUTCString date))

(defn rss-feed-xml []
  (let [now (rss-date (js/Date.))]
    [:rss {:xmlns:atom "http://www.w3.org/2005/Atom" :version "2.0"}
     [:channel
      [:title "ClojureScript"]
      [:description "ClojureScript News and Releases"]
      [:link "http://cljsinfo.github.io/news"]
      [:atom:link {:href (str urls/root urls/news-feed)
                   :rel "self"
                   :type "application/rss+xml"}]
      [:pubDate now]
      [:lastBuildDate now]
      [:generator "(sitegen.news/rss-feed-xml)"]
      (for [{:keys [title html-body date url]}
            (take 10 (reverse posts))]
        [:item
          [:title title]
          [:description (goog.string.htmlEscape html-body true)]
          [:pubDate (rss-date date)]
          [:link (urls/pretty (str urls/root url))]])]]))

(defn create-index-page! []
  (->> (index-page)
       (common-layout)
       (hiccup/render)
       (urls/write! urls/news-index)))

(defn create-post-pages! []
  (doseq [post posts]
    (->> (post-page post)
         (common-layout)
         (hiccup/render)
         (urls/write! (:url post)))))

(defn create-rss-feed! []
  (->> (rss-feed-xml)
       (hiccup/render)
       (urls/write! urls/news-feed)))

(defn render! []
  (urls/make-dir! urls/news-dir)
  (create-index-page!)
  (create-post-pages!)
  (create-rss-feed!))
