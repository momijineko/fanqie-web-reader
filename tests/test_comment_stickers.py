"""Sticker placeholders like [哭] should become stickers, not photo attachments."""

from routers.comments import _normalize_comments, _normalize_replies


def test_normalize_comments_maps_sticker_placeholder_to_stickers():
    raw = {
        "code": 0,
        "data": {
            "data_list": [
                {
                    "comment": {
                        "comment_id": "7671158225588863806",
                        "common": {
                            "content": {
                                "text": "[哭]",
                                "image_data_list": {
                                    "image_data": [
                                        {
                                            "image_type": 1,
                                            "web_uri": "https://p3-reading-sign.fqnovelpic.com/sticker-thumb.heic",
                                            "web_url": "https://p3-reading-sign.fqnovelpic.com/sticker-full.heic",
                                            "expand_web_url": "https://p3-reading-sign.fqnovelpic.com/sticker-full.heic",
                                            "width": 420,
                                            "height": 290,
                                        }
                                    ]
                                },
                            },
                            "create_timestamp": 1786082602,
                            "user_info": {
                                "base_info": {"user_name": "汐汐不嘻曦", "user_avatar": ""}
                            },
                        },
                        "stat": {"digg_count": 5, "reply_count": 0},
                    }
                }
            ]
        },
    }
    comments = _normalize_comments(raw)
    assert len(comments) == 1
    c = comments[0]
    assert c["content"] == "[哭]"
    assert "images" not in c
    assert len(c["stickers"]) == 1
    assert c["stickers"][0]["name"] == "哭"
    # Prefer smaller web_uri for inline stickers
    assert c["stickers"][0]["url"].endswith("sticker-thumb.heic")


def test_normalize_comments_keeps_photos_when_text_is_not_placeholder():
    raw = {
        "code": 0,
        "data": {
            "data_list": [
                {
                    "comment": {
                        "comment_id": "1",
                        "common": {
                            "content": {
                                "text": "居然是八岐大蛇",
                                "image_data_list": {
                                    "image_data": [
                                        {
                                            "image_type": 1,
                                            "web_url": "https://p3-reading-sign.fqnovelpic.com/photo.heic",
                                            "web_uri": "https://p3-reading-sign.fqnovelpic.com/photo-thumb.heic",
                                        }
                                    ]
                                },
                            },
                            "user_info": {"base_info": {"user_name": "读者", "user_avatar": ""}},
                        },
                        "stat": {"digg_count": 1, "reply_count": 0},
                    }
                }
            ]
        },
    }
    comments = _normalize_comments(raw)
    assert "stickers" not in comments[0]
    assert comments[0]["images"] == [
        "https://p3-reading-sign.fqnovelpic.com/photo.heic"
    ]


def test_normalize_replies_extracts_animated_sticker():
    raw = {
        "code": 0,
        "data": {
            "reply_list": [
                {
                    "reply_id": "88",
                    "Common": {
                        "content": {
                            "text": "[震惊]",
                            "image_data_list": {
                                "image_data": [
                                    {
                                        "image_type": 2,
                                        "dynamic_url": "https://p3-reading-sign.fqnovelpic.com/shock.awebp",
                                        "web_url": "https://p3-reading-sign.fqnovelpic.com/shock-still.heic",
                                    }
                                ]
                            },
                        },
                        "user_info": {
                            "base_info": {"user_name": "路人", "user_avatar": ""}
                        },
                    },
                    "stat": {"digg_count": 0},
                }
            ]
        },
    }
    replies = _normalize_replies(raw)
    assert len(replies) == 1
    assert replies[0]["stickers"][0]["name"] == "震惊"
    assert replies[0]["stickers"][0]["url"].endswith("shock.awebp")
    assert "images" not in replies[0]
