/**
 * Rivy email forwarder.
 *
 * Runs inside the Gmail account that receives the bank emails and sends each
 * new one to the Rivy server. The server turns it into a draft transaction.
 *
 * Setup is described in README.md next to this file.
 */

// Addresses the server knows how to read. Other mail never leaves Gmail.
var BANK_SENDERS = [
  'bca@bca.co.id',
  'noreply@jago.com',
  'receipts@blubybcadigital.id',
];

// How far back the very first run looks. Older emails are left alone, so
// transactions already recorded by hand do not come back as drafts.
var FIRST_RUN_LOOKBACK_HOURS = 24;

// Every run looks this far before the previous run again. Gmail can file an
// email late; the server ignores emails it already has.
var OVERLAP_MINUTES = 60;

// The server refuses requests above 2 MB.
var MAX_HTML_LENGTH = 1500000;

/** Run this once by hand. It asks for permission and starts the schedule. */
function install() {
  settings_();
  ScriptApp.getProjectTriggers().forEach(function (trigger) {
    if (trigger.getHandlerFunction() === 'forwardBankEmails') {
      ScriptApp.deleteTrigger(trigger);
    }
  });
  ScriptApp.newTrigger('forwardBankEmails').timeBased().everyMinutes(5).create();
  forwardBankEmails();
}

/** Stops the schedule. */
function uninstall() {
  ScriptApp.getProjectTriggers().forEach(function (trigger) {
    if (trigger.getHandlerFunction() === 'forwardBankEmails') {
      ScriptApp.deleteTrigger(trigger);
    }
  });
}

/** Sends the bank emails that arrived since the last successful run. */
function forwardBankEmails() {
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(1000)) {
    return; // the previous run is still busy
  }
  try {
    var config = settings_();
    var properties = PropertiesService.getScriptProperties();
    var startedAt = Date.now();
    var lastRun = Number(properties.getProperty('LAST_RUN_MS')) || 0;
    var cutoff = lastRun
      ? lastRun - OVERLAP_MINUTES * 60 * 1000
      : startedAt - FIRST_RUN_LOOKBACK_HOURS * 60 * 60 * 1000;

    var query = 'from:(' + BANK_SENDERS.join(' OR ') + ') after:' + Math.floor(cutoff / 1000);
    var counts = {};
    var failed = 0;

    var threads = GmailApp.search(query, 0, 100);
    threads.forEach(function (thread) {
      thread.getMessages().forEach(function (message) {
        // A thread also holds older emails with the same subject.
        if (message.getDate().getTime() < cutoff || !isBankSender_(message.getFrom())) {
          return;
        }
        var result = send_(config, message);
        if (result === null) {
          failed++;
        } else {
          counts[result] = (counts[result] || 0) + 1;
        }
      });
    });

    console.log('Rivy forwarder: ' + JSON.stringify(counts) + ', failed: ' + failed);
    // After a failure the same window is tried again next time.
    if (failed === 0) {
      properties.setProperty('LAST_RUN_MS', String(startedAt));
    }
  } finally {
    lock.releaseLock();
  }
}

/** Sends one email. Returns the server's result, or null when it failed. */
function send_(config, message) {
  var html = message.getBody() || '';
  var payload = {
    message_id: message.getHeader('Message-ID') || message.getId(),
    from: message.getFrom(),
    subject: message.getSubject(),
    date: message.getDate().toISOString(),
    html: html.length > MAX_HTML_LENGTH ? '' : html,
    text: message.getPlainBody() || '',
  };
  try {
    var response = UrlFetchApp.fetch(config.url + '/v1/inbox/email', {
      method: 'post',
      contentType: 'application/json',
      headers: { Authorization: 'Bearer ' + config.token },
      payload: JSON.stringify(payload),
      muteHttpExceptions: true,
    });
    if (response.getResponseCode() !== 200) {
      console.error('Rivy server answered ' + response.getResponseCode() + ': ' + response.getContentText().slice(0, 200));
      return null;
    }
    return JSON.parse(response.getContentText()).result || 'unknown';
  } catch (error) {
    console.error('Rivy server could not be reached: ' + error);
    return null;
  }
}

function isBankSender_(from) {
  var match = /<([^>]+)>/.exec(from);
  var address = (match ? match[1] : from).trim().toLowerCase();
  return BANK_SENDERS.indexOf(address) !== -1;
}

function settings_() {
  var properties = PropertiesService.getScriptProperties();
  var url = (properties.getProperty('RIVY_URL') || '').replace(/\/+$/, '');
  var token = properties.getProperty('RIVY_INGEST_TOKEN') || '';
  if (!/^https:\/\//.test(url)) {
    throw new Error('Set the script property RIVY_URL to the https address of the Rivy server.');
  }
  if (!token) {
    throw new Error('Set the script property RIVY_INGEST_TOKEN to the same value as on the server.');
  }
  return { url: url, token: token };
}
