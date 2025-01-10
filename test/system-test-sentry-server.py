#!/usr/bin/env python3

from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse
import sys
import threading
import binascii
import json
import gzip

apiOrg = 'sentry-sdks'
apiProject = 'sentry-java'
uri = urlparse(sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:8000')
version='1.1.0'
appIdentifier='com.sentry.fastlane.app'

class EnvelopeStorage:
    __envelopes_received = []

    @classmethod
    def add(cls, envelope):
        cls.__envelopes_received.append(envelope)

    @classmethod
    def get_envelopes_received(cls):
        return cls.__envelopes_received

    @classmethod
    def get_json(cls):
        jsonObject = {
            'envelopes': cls.__envelopes_received
        }
        return json.dumps(jsonObject)

    @classmethod
    def reset(cls):
        cls.__envelopes_received.clear()

class EnvelopeCount:
    __envelopes_received = 0

    @classmethod
    def increment(cls):
        cls.__envelopes_received += 1

    @classmethod
    def get_envelopes_received(cls):
        return cls.__envelopes_received

    @classmethod
    def get_json(cls):
        jsonObject = {
            'envelopes': cls.__envelopes_received
        }
        return json.dumps(jsonObject)

    @classmethod
    def reset(cls):
        cls.__envelopes_received = 0


class Handler(BaseHTTPRequestHandler):
    body = None

    def do_GET(self):
        self.start_response(HTTPStatus.OK)

        if self.path == "/STOP":
            print("HTTP server stopping!")
            threading.Thread(target=self.server.shutdown).start()
            return

        if self.path == "/envelope-count":
            print("Envelope count queried " + str(EnvelopeCount.get_envelopes_received()))
            self.writeJSON(EnvelopeCount.get_json())
            return

        if self.path == "/envelopes-received":
            print("Envelopes queried ")
            self.writeJSON(EnvelopeStorage.get_json())
            return

        if self.path == "/reset":
            print("Envelopes reset")
            EnvelopeStorage.reset()
            EnvelopeCount.reset()
            self.writeJSON(json.dumps({}))
            return

        self.flushLogs()

    def do_POST(self):
        if self.isApi('/api/0/envelope/'):
            EnvelopeCount.increment()
            self.start_response(HTTPStatus.OK)
            self.end_headers()

        self.flushLogs()

    def do_PUT(self):
        self.start_response(HTTPStatus.OK)
        self.end_headers()

        self.flushLogs()

    def start_response(self, code):
        self.body = None
        self.log_request(code)
        self.send_response_only(code)

    def log_request(self, code=None, size=None):
        if isinstance(code, HTTPStatus):
            code = code.value
        body = self.body = self.requestBody()

        if body:
            EnvelopeStorage.add(str(body))
            body = self.body[0:min(1000, len(body))]
        self.log_message('"%s" %s %s%s',
                         self.requestline, str(code), "({} bytes)".format(len(body)) if size else '', body)

    # Note: this may only be called once during a single request - can't `.read()` the same stream again.
    def requestBody(self):
        if self.command == "POST" and 'Content-Length' in self.headers:
            content = self.getContent()

            try:
                return content.decode("utf-8", errors='replace')
            except Exception as e:
                print(e)
                return binascii.hexlify(bytearray(content))
        return None

    def isApi(self, api: str):
        if self.path.strip('/') == api.strip('/'):
            self.log_message("Matched API endpoint {}".format(api))
            return True
        return False

    def writeJSONFile(self, file_name: str):
        json_file = open(file_name, "r")
        self.writeJSON(json_file.read())
        json_file.close()

    def writeJSON(self, string: str):
        self.send_header("Content-type", "application/json")
        self.end_headers()
        self.wfile.write(str.encode(string))

    def flushLogs(self):
        sys.stdout.flush()
        sys.stderr.flush()

    def getContent(self):
        length = int(self.headers['Content-Length'])
        content = self.rfile.read(length)

        if 'Content-Encoding' in self.headers and self.headers['Content-Encoding'] == 'gzip':
            content = gzip.decompress(content)

        return content


print("HTTP server listening on {}".format(uri.geturl()))
print("To stop the server, execute a GET request to {}/STOP".format(uri.geturl()))
httpd = ThreadingHTTPServer((uri.hostname, uri.port), Handler)
target = httpd.serve_forever()
