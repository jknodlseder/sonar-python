from typing import Union

def url2pathname(pathname: str) -> str: ...
def pathname2url(pathname: str) -> str: ...
def _pncomp2url(component: Union[str, bytes]) -> str: ...