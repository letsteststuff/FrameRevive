package com.framerevive.app

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One active image operation and only the most recently requested pending operation. */
class LatestPhotoExecutor : ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
    ArrayBlockingQueue<Runnable>(1), DiscardOldestPolicy())
