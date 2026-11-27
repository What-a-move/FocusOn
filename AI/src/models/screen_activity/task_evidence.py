"""Compare visible operation names and code symbols with a confirmed task profile."""
import re

# Common operations can be corroborated when the captured documentation uses another language.
_OPERATION_ALIASES = {
    "routing": r"라우팅|\brout(?:e|es|ing)\b",
    "middleware": r"미들웨어|\bmiddleware\b",
    "upload": r"업로드|\bupload(?:s|ed|ing)?\b",
    "download": r"다운로드|\bdownload(?:s|ed|ing)?\b",
    "aggregation": r"집계|\baggregat(?:e|es|ion|ing)\b",
    "pagination": r"페이지네이션|페이징|\bpaginat(?:e|es|ion|ing)\b",
    "authentication": r"인증|\bauthenticat(?:e|es|ion|ing)\b",
    "authorization": r"인가|\bauthoriz(?:e|es|ation|ing)\b",
    "migration": r"마이그레이션|\bmigrat(?:e|es|ion|ing)\b",
    "deployment": r"배포|\bdeploy(?:s|ed|ment|ing)?\b",
    "exposure": r"노출|\bexpos(?:e|es|ed|ure|ing)\b",
    "borrowing": r"빌림|\bborrow(?:s|ed|ing)?\b",
    "ownership": r"소유권|\bownership\b",
    "thread": r"스레드|\bthread(?:s|ed|ing)?\b",
    "caching": r"캐시|캐싱|\bcach(?:e|es|ed|ing)\b",
    "replication": r"복제|\breplicat(?:e|es|ion|ing)\b",
    "matrix_multiplication": r"행렬\s*곱셈|\bmatrix multiplication\b",
}
OPERATIONS = tuple((name, re.compile(pattern, re.I)) for name, pattern in _OPERATION_ALIASES.items())
WORD = re.compile(r"[A-Za-z][A-Za-z0-9_]*|[가-힣]+")
OPERATOR = re.compile(r"(?<![\w$])\$[A-Za-z][\w]*\b")
CALL = re.compile(r"(?<![\w$])(?:[A-Za-z_][\w]*\.)*([A-Za-z_][\w]*)\s*\(")
SUFFIX = re.compile(r"(?:하기|하는|에서|으로|이나|하고|의|과|와|를|을|로)$")
COMMON = frozenset({
    "구현", "공부", "학습", "실습", "이해", "연습", "만들기", "사용", "활용", "처리", "기초", "기본",
    "개념", "방법", "작성", "정리", "모듈", "관리", "설정", "테스트", "예제", "비교", "동작", "패턴",
    "함수", "객체", "코드", "시스템", "자료", "프로젝트", "문서", "데이터", "단계", "읽기", "쓰기",
    "learn", "using", "use", "example", "guide", "tutorial", "introduction", "reference", "api",
    "ideas", "core", "basic", "advanced", "code", "data", "system", "docs", "documentation",
})


class Evidence:
    def __init__(self, text, family_patterns=()):
        without_family = text
        for pattern in family_patterns:
            without_family = pattern.sub(" ", without_family)
        operations = set()
        for name, pattern in OPERATIONS:
            if pattern.search(without_family):
                operations.add(name)
                without_family = pattern.sub(" ", without_family)
        self.operations = operations
        self.words = {
            SUFFIX.sub("", word.casefold()) for word in WORD.findall(without_family)
        } - COMMON - {""}
        self.symbols = set(OPERATOR.findall(text)) | {name.casefold() for name in CALL.findall(text)}

    def against(self, screen, compatible_family):
        operations = len(self.operations & screen.operations)
        words = self.words & screen.words
        symbols = len(self.symbols & screen.symbols)
        # A qualified API vocabulary can identify the operation even in an untitled code editor.
        strong = symbols >= 2 or ((compatible_family or words) and symbols > 0)
        # Same technology alone never accounts for this operation evidence.
        strong = strong or (operations > 0 and (compatible_family or bool(words)))
        return strong, operations * 2 + len(words) + symbols * 3
