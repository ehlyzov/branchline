@file:Suppress("UnsafeCastFromDynamic")

package v2.runtime.io

// Register browser fallbacks onto globalThis so generic jsMain actuals can delegate safely.
@Suppress("UNUSED_VARIABLE")
private val __initBrowserIO = run {
    js(
        """
        (function() {
          if (typeof globalThis !== 'undefined') {
            globalThis.__blWriteText = function(path, text) { throw new Error('writeText is not supported in browser JS'); };
            globalThis.__blReadText = function(path) { throw new Error('readText is not supported in browser JS'); };
          }
        })();
        """
    )
    0
}
