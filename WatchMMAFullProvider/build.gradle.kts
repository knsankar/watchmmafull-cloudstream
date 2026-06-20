// use an integer for version numbers
version = 1


cloudstream {
    language = "en"
    description = "Fight replay provider for WatchMMAFull."
    authors = listOf("TRAE")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    tvTypes = listOf("Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=watchmmafull.com&sz=%size%"
}
