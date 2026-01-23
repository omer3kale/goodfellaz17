package com.goodfellaz.proxy.service;

import com.goodfellaz.proxy.model.ProxyNode;
import java.util.List;

public interface ProxySelector {
    ProxyNode select(List<ProxyNode> candidates);
}
