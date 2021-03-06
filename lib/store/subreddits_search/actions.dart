import 'dart:async';

import 'package:dio/dio.dart';
import 'package:reddigram/api/api.dart';
import 'package:reddigram/store/store.dart';
import 'package:redux/redux.dart';
import 'package:redux_thunk/redux_thunk.dart';

ThunkAction<ReddigramState> searchSubreddits(String query,
    {Completer completer}) {
  return (Store<ReddigramState> store) {
    redditRepository.searchSubreddits(query).then((subreddits) async {
      // If there was no subreddit with this particular name...
      if (!subreddits.any(
          (subreddit) => subreddit.name.toLowerCase() == query.toLowerCase())) {
        // ...but this subreddit exists...
        redditRepository.subreddit(query).then((subreddit) {
          store.dispatch(FetchedSubreddits([subreddit]));
          // ...then prepend it to fetched search subreddits.
          store.dispatch(FetchedSearchSubreddits(
            query,
            [subreddit.id, ...subreddits.map((subreddit) => subreddit.id)],
          ));
        }).whenComplete(() => completer?.complete());
      } else {
        completer?.complete();
      }

      store.dispatch(FetchedSubreddits(subreddits));
      store.dispatch(FetchedSearchSubreddits(
          query, subreddits.map((subreddit) => subreddit.id).toList()));
    }, onError: (error) {
      // Sometimes (well, not that rarely) Reddit's search service
      // isn't working, fetch the exact subreddit only then.
      if (error is DioError && error.response.statusCode == 503) {
        redditRepository.subreddit(query).then((subreddit) {
          store.dispatch(FetchedSubreddits([subreddit]));
          store.dispatch(FetchedSearchSubreddits(query, [subreddit.id]));
        }).whenComplete(() => completer?.complete());
      }
    });
  };
}

class ClearSearch {}

class FetchedSearchSubreddits {
  final String query;
  final List<String> resultSubredditsIds;

  FetchedSearchSubreddits(this.query, this.resultSubredditsIds);
}
