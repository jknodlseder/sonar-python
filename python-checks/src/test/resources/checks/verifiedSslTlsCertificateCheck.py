def pyopensslTest():
  # Mutably borrowed from here:
  # https://github.com/SonarSource/security-expected-issues/blob/master/python/rules/vulnerabilities/\
  # RSPEC-4830%20Server%20certificates%20should%20be%20verified%20during%20SSL%E2%81%84TLS%20connections/\
  # pyopenssl-test.py

  from OpenSSL import SSL
  import sys, os, select, socket

  ctx1 = SSL.Context(SSL.TLSv1_2_METHOD)
  ctx1.set_verify(SSL.VERIFY_PEER, verify_callback) # Compliant
  ctx1.set_verify(SSL.VERIFY_PEER | SSL.VERIFY_FAIL_IF_NO_PEER_CERT, verify_callback) # Compliant
  ctx1.set_verify(SSL.VERIFY_PEER | SSL.VERIFY_FAIL_IF_NO_PEER_CERT | VERIFY_CLIENT_ONCE, verify_callback) # Compliant

  ctx = SSL.Context(SSL.TLSv1_2_METHOD)
  ctx.set_verify(SSL.VERIFY_NONE, verify_callback) # Noncompliant {{Omitting the check of the peer certificate is dangerous.}}
  #                  ^^^^^^^^^^^

  ctx.set_verify(SSL.VERIFY_FAIL_IF_NO_PEER_CERT | SSL.VERIFY_NONE | SSL.VERIFY_CLIENT_ONCE) # Noncompliant {{Omitting the check of the peer certificate is dangerous.}}
  #                                                    ^^^^^^^^^^^

  # Weird cases for code coverage only.
  ctxC1 = SSL.Context(SSL.TLSv1_2_METHOD)
  ctxC1.set_verify()
  kwargs = { 'something': True }
  ctxC1.set_verify(**kwargs)
  ctxC1.set_verify(noSSL.THIS_DOESNT_EXIST)


def requestsTests():
  # Mutably borrowed from here:
  # https://github.com/SonarSource/security-expected-issues/blob/master/python/rules/vulnerabilities/\
  # RSPEC-4830%20Server%20certificates%20should%20be%20verified%20during%20SSL%E2%81%84TLS%20connections/\
  # requests-tests.py
  import requests

  requests.request('GET', 'https://example.domain', verify=False) # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                        ^^^^^
  requests.request('GET', 'https://example.domain', verify='') # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                        ^^
  requests.request('GET', 'https://example.domain', verify=0) # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                        ^
  requests.request('GET', 'https://example.domain', verify=0.0) # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                        ^^^
  requests.request('GET', 'https://example.domain', verify=0j) # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                        ^^
  requests.request('GET', 'https://example.domain', verify="") # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                        ^^
  requests.request('GET', 'https://example.domain', verify=b'') # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                        ^^^
  requests.request('GET', 'https://example.domain', verify=[]) # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                        ^^
  requests.request('GET', 'https://example.domain', verify={}) # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                        ^^
  requests.request('GET', 'https://example.domain', verify=()) # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                        ^^
  requests.request('GET', 'https://example.domain', verify=set()) # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                        ^^^^^
  requests.request('GET', 'https://example.domain', verify=range(0)) # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                        ^^^^^^^^
  requests.request(verify=False, method='GET', url='https://example.domain') # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                       ^^^^^


  kargs1 = {'verify': False} # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                   ^^^^^
  requests.request('GET', 'https://example.domain', **kargs1)
  #                                                   ^^^^^^ < 1


  kargs2 = {'method': 'GET', 'url': 'https://example.domain', 'verify': False} # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                                     ^^^^^
  requests.request(**kargs2)
  #                  ^^^^^^ < 1

  requests.get('https://example.domain', verify=False) # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                             ^^^^^
  requests.post('https://example.domain', verify=False) # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                              ^^^^^
  requests.options('https://example.domain', verify=False) # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                                                 ^^^^^

  requests.request(method='GET', url='https://example.domain') # Compliant
  requests.request(method='GET', url='https://example.domain', verify=True) # Compliant
  requests.request('GET', 'https://example.domain', verify='/path/to/CAbundle') # Compliant
  requests.request(verify=True, method='GET', url='https://example.domain') # Compliant
  kargs = {'verify': True}
  requests.request('GET', 'https://example.domain', **kargs) # Compliant
  kargs = {'method': 'GET', 'url': 'https://example.domain', 'verify': True}
  requests.request(**kargs) # Compliant

  requests.head(url='https://example.domain') # Compliant
  requests.get(url='https://example.domain') # Compliant
  requests.post(url='https://example.domain') # Compliant
  requests.put(url='https://example.domain') # Compliant
  requests.patch(url='https://example.domain') # Compliant
  requests.delete(url='https://example.domain') # Compliant
  requests.options(url='https://example.domain') # Compliant
  requests.request('GET', 'https://example.domain', verify=range(42)) # Compliant
  requests.request('GET', 'https://example.domain', verify=range(2, 5)) # Compliant

  # Pathological cases for code coverage only
  requests.request('GET', 'https://example.domain', verify=thatThingIsNotACollectionConstructor(0))
  requests.request('GET', 'https://example.domain', verify=sorted([])) # FN, bool(sorted([])) == False
  ft = { 'from': 10, 'to': 100 }
  requests.request('GET', 'https://example.domain', verify=range(**ft))
  requests.request('GET', 'https://example.domain', verify=range("not numeric"))
  requests.request('GET', 'https://example.domain', verify=set(42))


def urllibTests():
  # Mutably borrowed from
  # https://raw.githubusercontent.com/SonarSource/security-expected-issues/master/python/rules/vulnerabilities/\
  # RSPEC-4830%20Server%20certificates%20should%20be%20verified%20during%20SSL%E2%81%84TLS%20connections/\
  # urllib-test.py
  import urllib.request
  import ssl
  import sys

  # (S4830) - bydefault = ctx.verify_mode = ssl.CERT_NONE
  ctx1 = ssl._create_unverified_context()  # Noncompliant {{Certificate verification is disabled by default, verify_mode should be updated.}}
  #          ^^^^^^^^^^^^^^^^^^^^^^^^^^

  ctx2 = ssl._create_unverified_context()
  ctx2.verify_mode = ssl.CERT_NONE # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                      ^^^^^^^^^

  ctx3 = ssl._create_unverified_context()
  ctx3.verify_mode = ssl.CERT_OPTIONAL # Compliant (S4830)

  ctx4 = ssl._create_unverified_context()
  ctx4.verify_mode = ssl.CERT_REQUIRED # Compliant (S4830)

  ctx5 = ssl._create_stdlib_context() # Noncompliant {{Certificate verification is disabled by default, verify_mode should be updated.}}
  #          ^^^^^^^^^^^^^^^^^^^^^^

  ctx6 = ssl._create_stdlib_context()
  ctx6.verify_mode = ssl.CERT_NONE # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                      ^^^^^^^^^

  ctx7 = ssl._create_stdlib_context()
  ctx7.verify_mode = ssl.CERT_OPTIONAL # Compliant (S4830)

  ctx8 = ssl._create_stdlib_context()
  ctx8.verify_mode = ssl.CERT_REQUIRED # Compliant (S4830)

  ctx9 = ssl.create_default_context()  # Compliant (S4830) - bydefault = ctx.verify_mode = ssl.CERT_REQUIRED

  ctx9b = ssl.create_default_context()
  ctx9b.check_hostname = False
  ctx9b.verify_mode = ssl.CERT_NONE # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                       ^^^^^^^^^

  ctxA = ssl.create_default_context()
  ctxA.verify_mode = ssl.CERT_OPTIONAL # Compliant (S4830)

  ctxB = ssl.create_default_context()
  ctxB.verify_mode = ssl.CERT_REQUIRED # Compliant (S4830)

  ctxC = ssl._create_default_https_context() # Compliant (S4830) - bydefault = ctx.verify_mode = ssl.CERT_REQUIRED

  ctxD = ssl._create_default_https_context()
  ctxD.check_hostname = False
  ctxD.verify_mode = ssl.CERT_NONE # Noncompliant {{Disabling certificate verification is dangerous.}}
  #                      ^^^^^^^^^

  ctxE = ssl._create_default_https_context()
  ctxE.verify_mode = ssl.CERT_OPTIONAL # Compliant (S4830)

  ctxF = ssl._create_default_https_context()
  ctxF.verify_mode = ssl.CERT_REQUIRED # Compliant (S4830)

  # Corner cases for code coverage
  ctxC1 = ssl.there_is_no_such_symbol()
  ctxC1.verify_mode = ssl.CERT_REQUIRED

  ctxC2 = ssl._create_default_https_context()
  ctxC2.verify_mode = ssl.THAT_S_NOT_A_VALID_MODE

  ambiguous = "" if 42 * 42 < 1700 else (lambda x: x * x)
  ctxC3 = ambiguous._create_default_https_context()
  ctxC4 = notASymbol()