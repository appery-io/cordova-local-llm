/**
 * Cordova plugin for on-device LLM (iOS: Apple Intelligence / Foundation Models).
 *
 * API mirrors @capacitor/local-llm where possible.
 */

function execPromise(action, args) {
  return new Promise(function (resolve, reject) {
    cordova.exec(resolve, reject, 'LocalLLM', action, args || []);
  });
}

function execError(err) {
  if (typeof err === 'string') {
    return { code: 'LOCAL_LLM_UNKNOWN_ERROR', message: err };
  }
  if (err && typeof err === 'object') {
    return {
      code: err.code || 'LOCAL_LLM_UNKNOWN_ERROR',
      message: err.message || String(err),
    };
  }
  return { code: 'LOCAL_LLM_UNKNOWN_ERROR', message: 'Unknown error' };
}

function makeListenerId() {
  return 'llm_' + Date.now() + '_' + Math.random().toString(36).slice(2);
}

var LocalLLM = {
  /**
   * @returns {Promise<{status: string}>}
   */
  systemAvailability: function () {
    return execPromise('systemAvailability', []);
  },

  /**
   * Not supported on iOS (Android-only in Capacitor version).
   */
  download: function () {
    return execPromise('download', []);
  },

  /**
   * @param {{ prompt: string, sessionId?: string, instructions?: string, options?: object }} options
   * @returns {Promise<{text: string}>}
   */
  prompt: function (options) {
    return execPromise('prompt', [options || {}]);
  },

  /**
   * @param {{ sessionId: string }} options
   */
  endSession: function (options) {
    return execPromise('endSession', [options || {}]);
  },

  /**
   * @param {{ prompt: string, promptImages?: string[], count?: number }} options
   * @returns {Promise<{pngBase64Images: string[]}>}
   */
  generateImage: function (options) {
    return execPromise('generateImage', [options || {}]);
  },

  /**
   * @param {{ sessionId: string, promptPrefix?: string }} options
   */
  warmup: function (options) {
    return execPromise('warmup', [options || {}]);
  },

  /**
   * @param {'systemAvailabilityChange'} eventName
   * @param {(response: {status: string}) => void} listenerFunc
   * @returns {Promise<{remove: function(): Promise<void>}>}
   */
  addListener: function (eventName, listenerFunc) {
    if (eventName !== 'systemAvailabilityChange') {
      return Promise.reject(new Error('Unknown event: ' + eventName));
    }

    var listenerId = makeListenerId();

    return new Promise(function (resolve, reject) {
      cordova.exec(
        function (result) {
          listenerFunc(result);
        },
        function (err) {
          reject(execError(err));
        },
        'LocalLLM',
        'addAvailabilityListener',
        [listenerId]
      );

      resolve({
        remove: function () {
          return execPromise('removeAvailabilityListener', [listenerId]);
        },
      });
    });
  },

  removeAllListeners: function () {
    return execPromise('removeAllListeners', []);
  },
};

module.exports = LocalLLM;
