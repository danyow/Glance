import 'dart:io';

import 'package:built_collection/built_collection.dart';
import 'package:dio/dio.dart';
import 'package:package_info/package_info.dart';
import 'package:reddigram/api/api.dart';

class RedditRepository {
  Dio client;

  RedditRepository() {
    client = Dio(BaseOptions(
      baseUrl: 'https://reddit.com',
    ));

    PackageInfo.fromPlatform().then((info) =>
        client.options.headers['User-Agent'] =
            '${info.packageName}:${info.version} (by /u/Albert221)');
  }

  set accessToken(String accessToken) {
    if (accessToken != null) {
      client.options.headers['Authorization'] = 'bearer $accessToken';
      client.options.baseUrl = 'https://oauth.reddit.com';
    } else {
      client.options.headers.remove('Authorization');
      client.options.baseUrl = 'https://reddit.com';
    }
  }

  static ListingResponse _filterOnlyPhotos(ListingResponse response) {
    return response.rebuild((b) => b
      ..data = response.data
          .rebuild((b) => b
            ..children = ListBuilder(response.data.children
                .where((child) => child.data.preview != null)))
          .toBuilder());
  }

  void _assertAuthorized() {
    assert(
        client.options.headers.containsKey('Authorization'), 'User required.');
  }

  Future<ListingResponse> subreddit(String name,
      {String after = '', int limit = 25}) async {
    return client
        .get('/r/$name.json?after=$after&limit=$limit')
        .then((response) => serializers.deserializeWith(
            ListingResponse.serializer, response.data))
        .then(_filterOnlyPhotos);
  }

  Future<String> username() async {
    _assertAuthorized();

    return client.get('/api/v1/me').then((response) => response.data['name']);
  }

  Future<void> upvote(String id) async {
    _assertAuthorized();

    return client.post('/api/vote',
        data: 'dir=1&id=$id',
        options: Options(
            contentType:
                ContentType.parse('application/x-www-form-urlencoded')));
  }

  Future<void> cancelUpvote(String id) async {
    _assertAuthorized();

    return client.post('/api/vote',
        data: 'dir=0&id=$id',
        options: Options(
            contentType:
                ContentType.parse('application/x-www-form-urlencoded')));
  }
}
