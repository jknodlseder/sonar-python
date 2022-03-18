from SonarPythonAnalyzerFakeStub import CustomStubBase

class HttpResponseBase(CustomStubBase):
    def set_cookie(self, *args, **kwargs) -> None: ...
    def set_signed_cookie(self, *args, **kwargs) -> None: ...
    def __setitem__(self, *args, **kwargs) -> None: ...
    def setdefault(self, *args, **kwargs) -> None: ...

class HttpResponse(HttpResponseBase): ...
class HttpResponseRedirect(HttpResponseBase): ...
class HttpResponsePermanentRedirect(HttpResponseBase): ...
class HttpResponseNotModified(HttpResponseBase): ...
class HttpResponseNotFound(HttpResponseBase): ...
class HttpResponseForbidden(HttpResponseBase): ...
class HttpResponseNotAllowed(HttpResponseBase): ...
class HttpResponseGone(HttpResponseBase): ...
class HttpResponseServerError(HttpResponseBase): ...
class HttpResponseBadRequest(HttpResponseBase): ...