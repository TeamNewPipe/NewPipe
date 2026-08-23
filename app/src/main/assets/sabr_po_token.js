/* global PipePlayWebViewBridge */

function pipeplayBridge() {
  return window["PipePlayWebViewBridge"];
}

function loadBotGuard(root, challengeData, onReady, onError) {
  var intervalId;
  var finished = false;

  function fail(error) {
    if (finished) {
      return;
    }
    finished = true;
    if (intervalId) {
      clearInterval(intervalId);
    }
    onError(error);
  }

  try {
    root.vm = root[challengeData.globalName];
    root.program = challengeData.program;
    root.vmFunctions = {};
    root.syncSnapshotFunction = null;

    if (!root.vm) {
      throw new Error("[BotGuardClient]: VM not found in the global object");
    }
    if (!root.vm.a) {
      throw new Error("[BotGuardClient]: Could not load program");
    }

    var vmFunctionsCallback = function (
      asyncSnapshotFunction,
      shutdownFunction,
      passEventFunction,
      checkCameraFunction
    ) {
      root.vmFunctions = {
        asyncSnapshotFunction: asyncSnapshotFunction,
        shutdownFunction: shutdownFunction,
        passEventFunction: passEventFunction,
        checkCameraFunction: checkCameraFunction,
      };
    };

    var noOp = function () {};
    var loggerFunctions = [noOp, noOp, noOp, noOp, noOp];

    root.syncSnapshotFunction = root.vm.a(
      root.program,
      vmFunctionsCallback,
      true,
      root.userInteractionElement,
      noOp,
      [[], []],
      undefined,
      false,
      loggerFunctions
    )[0];

    root._botGuardPolls = 0;
    intervalId = setInterval(function () {
      if (root.vmFunctions.asyncSnapshotFunction) {
        finished = true;
        clearInterval(intervalId);
        onReady(root);
        return;
      }
      if (root._botGuardPolls >= 10000) {
        fail(new Error("asyncSnapshotFunction is null even after 10 seconds"));
        return;
      }
      root._botGuardPolls = (root._botGuardPolls || 0) + 1;
    }, 1);
  } catch (error) {
    fail(error);
  }
}

function snapshot(root, args, onSuccess, onError) {
  try {
    if (!root.vmFunctions.asyncSnapshotFunction) {
      throw new Error("[BotGuardClient]: Async snapshot function not found");
    }

    root.vmFunctions.asyncSnapshotFunction(
      function (response) {
        onSuccess(response);
      },
      [
        args.contentBinding,
        args.signedTimestamp,
        args.webPoSignalOutput,
        args.skipPrivacyBuffer,
      ]
    );
  } catch (error) {
    onError(error);
  }
}

function runBotGuard(challengeData, onSuccess, onError) {
  var root = this;
  try {
    var interpreterJavascript =
      challengeData.interpreterJavascript
        .privateDoNotAccessOrElseSafeScriptWrappedValue;

    if (!interpreterJavascript) {
      throw new Error("Could not load VM");
    }

    new Function(interpreterJavascript)();

    var webPoSignalOutput = [];
    loadBotGuard(
      root,
      {
        globalName: challengeData.globalName,
        globalObj: root,
        program: challengeData.program,
      },
      function (botguard) {
        snapshot(
          botguard,
          { webPoSignalOutput: webPoSignalOutput },
          function (botguardResponse) {
            onSuccess({
              webPoSignalOutput: webPoSignalOutput,
              botguardResponse: botguardResponse,
            });
          },
          onError
        );
      },
      onError
    );
  } catch (error) {
    onError(error);
  }
}

function createPoTokenMinter(webPoSignalOutput, integrityToken) {
  var getMinter = webPoSignalOutput[0];

  if (!getMinter) {
    throw new Error("PMD:Undefined");
  }

  var mintCallback = getMinter(integrityToken);

  if (!(mintCallback instanceof Function)) {
    throw new Error("APF:Failed");
  }

  return mintCallback;
}

function obtainPoToken(mintCallback, identifier) {
  var result = mintCallback(identifier);

  if (!result) {
    throw new Error("YNJ:Undefined");
  }

  if (!(result instanceof Uint8Array)) {
    throw new Error("ODM:Invalid");
  }

  return result;
}

function pipeplaySabrRunBotguard(sessionId, eventId, challengeData) {
  var bridge = pipeplayBridge();
  try {
    window.yt = window.yt || {};
    window.yt.config_ = window.yt.config_ || {};
    window.yt.config_.EVENT_ID = eventId;
    runBotGuard(
      challengeData,
      function (result) {
        window.__sabrLocalDomSessions = window.__sabrLocalDomSessions || {};
        window.__sabrLocalDomSessions[sessionId] = {
          webPoSignalOutput: result.webPoSignalOutput,
        };
        bridge.onSabrLocalDomRunBotguardResult(
          sessionId,
          result.botguardResponse
        );
      },
      function (error) {
        bridge.onSabrLocalDomJsInitializationError(
          sessionId,
          String(error) + "\n" + (error && error.stack ? error.stack : "")
        );
      }
    );
  } catch (error) {
    bridge.onSabrLocalDomJsInitializationError(
      sessionId,
      String(error) + "\n" + (error && error.stack ? error.stack : "")
    );
  }
}

function pipeplaySabrCreateMinter(sessionId, integrityToken) {
  var bridge = pipeplayBridge();
  try {
    var sessions = window.__sabrLocalDomSessions || {};
    var session = sessions[sessionId];
    if (!session || !session.webPoSignalOutput) {
      throw new Error("Local DOM WebPO signal output is missing");
    }
    session.integrityToken = integrityToken;
    session.poTokenMinter = createPoTokenMinter(
      session.webPoSignalOutput,
      session.integrityToken
    );
    bridge.onSabrLocalDomMinterReady(sessionId);
  } catch (error) {
    bridge.onSabrLocalDomJsInitializationError(
      sessionId,
      String(error) + "\n" + (error && error.stack ? error.stack : "")
    );
  }
}

function pipeplaySabrObtainPoToken(sessionId, identifier, identifierU8) {
  var bridge = pipeplayBridge();
  try {
    var sessions = window.__sabrLocalDomSessions || {};
    var session = sessions[sessionId];
    if (!session || !session.poTokenMinter) {
      throw new Error("Local DOM PO token minter is not ready");
    }
    var poTokenU8 = obtainPoToken(session.poTokenMinter, identifierU8);
    var poTokenU8String = "";
    for (var i = 0; i < poTokenU8.length; i++) {
      if (i !== 0) {
        poTokenU8String += ",";
      }
      poTokenU8String += poTokenU8[i];
    }
    bridge.onSabrLocalDomObtainPoTokenResult(
      sessionId,
      identifier,
      poTokenU8String
    );
  } catch (error) {
    bridge.onSabrLocalDomObtainPoTokenError(
      sessionId,
      identifier,
      String(error) + "\n" + (error && error.stack ? error.stack : "")
    );
  }
}

function pipeplaySabrDeleteSession(sessionId) {
  if (window.__sabrLocalDomSessions) {
    delete window.__sabrLocalDomSessions[sessionId];
  }
}
