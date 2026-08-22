import Foundation

struct HttpRequest: Sendable, Equatable {
    var url: URL
    var method: String
    var headers: [String: String] = [:]
    var body: Data?
}

struct HttpResponse: Sendable, Equatable {
    var status: Int
    var body: Data
}

/// The seam the tests use. Everything above it is real code; only the socket is
/// faked, which is the same split the Android agent uses.
protocol Transport: Sendable {
    func send(_ request: HttpRequest) async throws -> HttpResponse
}

struct URLSessionTransport: Transport {
    let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func send(_ request: HttpRequest) async throws -> HttpResponse {
        var urlRequest = URLRequest(url: request.url)
        urlRequest.httpMethod = request.method
        urlRequest.httpBody = request.body
        for (name, value) in request.headers {
            urlRequest.setValue(value, forHTTPHeaderField: name)
        }
        // A child's phone is often on a slow tether; 30s is generous enough to
        // succeed and short enough that a background task still finishes.
        urlRequest.timeoutInterval = 30

        let (data, response) = try await session.data(for: urlRequest)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        return HttpResponse(status: status, body: data)
    }
}
