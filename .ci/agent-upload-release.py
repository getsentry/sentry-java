#!/usr/bin/env python

import os
import sys
import urlparse
import requests

try:
    from requests.packages import urllib3
    urllib3.disable_warnings()
except ImportError:
    pass


AUTH_USERNAME = 'getsentry-bot'
AUTH_TOKEN = os.environ['GITHUB_AUTH_TOKEN']
AUTH = (AUTH_USERNAME, AUTH_TOKEN)
TAG = os.environ.get('TRAVIS_TAG')
TARGET = os.environ.get('TARGET')
LIB = 'libsentry_agent_linux-%(target)s.%(ext)s'
EXT = 'so'
BIN_TYPE = os.environ.get('BIN_TYPE', 'release')
REPO = 'getsentry/sentry-java'


def log(message, *args):
    if args:
        message = message % args
    print >> sys.stderr, message


def api_request(method, path, **kwargs):
    url = urlparse.urljoin('https://api.github.com/', path.lstrip('/'))
    # default travis python does not have SNI
    return requests.request(method, url, auth=AUTH, verify=False, **kwargs)


def find_executable():
    path = LIB % {'target': TARGET, 'ext': EXT}
    log("Checking for executable: " + path)
    if os.path.isfile(path):
        return path


def ensure_release():
    resp = api_request('GET', 'repos/%s/releases' % REPO)
    resp.raise_for_status()
    for release in resp.json():
        if release['tag_name'] == TAG:
            log('Found already existing release %s' % release['id'])
            return release
    resp = api_request('POST', 'repos/%s/releases' % REPO, json={
        'tag_name': TAG,
        'name': 'sentry-java-agent %s' % TAG,
        'draft': True,
    })
    resp.raise_for_status()
    release = resp.json()
    log('Created new release %s' % release['id'])
    return release


def upload_asset(release, executable, target_name):
    resp = api_request('GET', release['assets_url'])
    resp.raise_for_status()
    for asset in resp.json():
        if asset['name'] == target_name:
            log('Already have release asset %s. Skipping' % target_name)
            return

    upload_url = release['upload_url'].split('{')[0]
    with open(executable, 'rb') as f:
        log('Creating new release asset %s.' % target_name)
        resp = api_request('POST', upload_url,
                           params={'name': target_name},
                           headers={'Content-Type': 'application/octet-stream'},
                           data=f)
        resp.raise_for_status()


def main():
    if not TAG:
        return log('No tag specified.  Doing nothing.')
    executable = find_executable()
    if executable is None:
        return log('Could not locate executable.  Doing nothing.')

    release = ensure_release()
    upload_asset(release, executable, executable)


if __name__ == '__main__':
    main()
