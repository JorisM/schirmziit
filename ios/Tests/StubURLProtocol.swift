import Foundation

/// Answers each request by URL and method, so the calls a screen makes can
/// succeed or fail independently — the shape both the parent read screens and
/// the parent writes issue them in.
///
/// Shared rather than duplicated per test file: two copies drift, and the one
/// that stops recording requests stops proving anything about what was sent.
final class StubURLProtocol: URLProtocol {
    /// Set by a test; returns the status and body for a request.
    static var handler: (@Sendable (URLRequest) -> (Int, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = Self.handler, let url = request.url else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL))
            return
        }
        let (status, body) = handler(request)
        let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

/// What the app actually put on the wire. Recorded so a test can assert the
/// method and path, not merely that *something* was sent.
struct SentRequest: Sendable {
    let method: String
    let path: String
}

/// `URLProtocol` hands its callbacks to whatever thread it likes, so the
/// recording has to be locked — an unsynchronised array here is a test suite
/// that fails once a week for no reason anyone can reproduce.
final class SentRequestLog: @unchecked Sendable {
    private let lock = NSLock()
    private var requests: [SentRequest] = []

    var all: [SentRequest] {
        lock.withLock { requests }
    }

    func record(_ request: SentRequest) {
        lock.withLock { requests.append(request) }
    }

    func clear() {
        lock.withLock { requests.removeAll() }
    }
}
