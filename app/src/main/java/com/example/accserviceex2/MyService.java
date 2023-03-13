package com.example.accserviceex2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class MyService extends AccessibilityService {
    class NodePair {
        public boolean isvisted;
        public int idx;

        public NodePair(int idx, boolean isvis) {
            this.idx = idx;
            this.isvisted = isvis;
        }
    }

    private static final String TAG = "MyService";


    //全局变量HashMap<key, value>
    //key为界面的唯一标识
    //value为该界面上可以点击跳转的按钮的list
    private HashMap<String, ArrayList<AccessibilityNodeInfo>> ClickedMap = new HashMap<>();

    private HashMap<String, NodePair> StrToNodeInfo = new HashMap<>();
    private static final String APP_PACKAGE_NAME = "com.example.myapplication"; // 要遍历的应用包名
    private static final Set<String> SYSTEM_APPS = new HashSet<>(Arrays.asList(
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.android.settings",
            "com.google.android.googlequicksearchbox",
            "com.google.android.gms"
    ));
    private LayoutInflater mInflater;
    private boolean mIsClicked = false; // 是否已经点击了目标组件
    private static int cnt = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        if (accessibilityEvent.getPackageName() == null || SYSTEM_APPS.contains(accessibilityEvent.getPackageName().toString())) {
            return;
        }

        if (accessibilityEvent.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            DealClickableWidget(accessibilityEvent);
//            printCurrentWindowWidget(accessibilityEvent);
        }

    }

    private void DealClickableWidget(AccessibilityEvent accessibilityEvent) {
        //下面的cnt代码主要是跳过一开始的framelayout
        if (cnt == 0) {
            cnt++;
            return;
        }
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo == null) {
            return;
        }
        DelayRendering();
        Log.d(TAG, "回调开始");
        //获取当前界面的唯一标识
        String pkgName = accessibilityEvent.getPackageName().toString();
        String className = accessibilityEvent.getClassName().toString();
        String currentWindowName = pkgName + "--" + className;
        Log.d(TAG, "进入当前界面为: " + currentWindowName);
        //如果map的value为空，则表示没创建过
        if (ClickedMap.get(currentWindowName) == null) {
            //接下来开始添加Map
            ArrayList<AccessibilityNodeInfo> nodeList = new ArrayList<>();
            addAllClickableNode(nodeList, nodeInfo);
            ClickedMap.put(currentWindowName, nodeList);
        }
        //遍历没有被访问过的可点击组件
        ArrayList<AccessibilityNodeInfo> nodeList = ClickedMap.get(currentWindowName);
        Log.d(TAG, "当前界面存在的可点击组件的个数: " + nodeList.size());
        if (nodeList.size() == 0) {
            Log.d(TAG, currentWindowName + "没有可点击组件，直接退出");
        }

        for (int i = 0; i < nodeList.size(); i++) {
            //需要判断该组件有没有被点击过
            AccessibilityNodeInfo node = nodeList.get(i);
            if (!IsNodeVisited(node, i)) {
                String nodeId = getUniqueId(node, i);
                NodePair nodePair = StrToNodeInfo.get(nodeId);
                nodePair.isvisted = true;
                StrToNodeInfo.put(nodeId, nodePair);
                Log.d(TAG, "处理可点击组件:" + nodeId);
                String currentWidgetClassName = node.getClassName().toString();

                //暂时不处理没有文本的
                //后期需要替换成其他的策略
                if (node.getText() == null) {
                    continue;
                }
                if (currentWidgetClassName.equals("android.widget.EditText")) {
                    //do nothing
                } else {
                    boolean isclick = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if (isclick) {
                        if (currentWidgetClassName.equals("android.widget.CheckBox")) {
                            Log.d(TAG, "点击后没跳转呢");
                        } else {
                            Log.d(TAG, "点击后跳转页面,return for循环");
                            return;
                        }
                    } else {
                        Log.d(TAG, "该组件不能被点击");
                    }
                }
            }
        }


        Log.d(TAG, "退出当前界面: " + currentWindowName);
        performGlobalAction(GLOBAL_ACTION_BACK);
        Log.d(TAG, "*****************************************");
    }

    private void printCurrentWindowWidget(AccessibilityEvent accessibilityEvent) {
        //下面的cnt代码主要是跳过一开始的framelayout
        if (cnt == 0) {
            cnt++;
            return;
        }
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo == null) {
            return;
        }
        //获取当前界面的唯一标识
        //打印当前界面
        String pkgName = accessibilityEvent.getPackageName().toString();
        String className = accessibilityEvent.getClassName().toString();
        String currentWindowName = pkgName + "--" + className;
        Log.d(TAG, "当前界面为: " + currentWindowName);
        ArrayList<AccessibilityNodeInfo> nodeList = new ArrayList<>();
        addAllClickableNode(nodeList, nodeInfo);
        //打印当前界面组件
        for (int i = 0; i < nodeList.size(); i++) {
            Log.d(TAG, getUniqueId(nodeList.get(i), i));
        }
        Log.d(TAG, "*****************************************");
    }

    public void DelayRendering() {
        long millis = 3000;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean IsNodeVisited(AccessibilityNodeInfo node, long seq) {
        String nodeUniqueId = getUniqueId(node, seq);
        NodePair nodeInfo = StrToNodeInfo.get(nodeUniqueId);
        return nodeInfo.isvisted;
    }

    //搜寻所有可以点击的节点并添加进nodeList中
    private void addAllClickableNode(ArrayList<AccessibilityNodeInfo> nodeList, AccessibilityNodeInfo node) {
        if (node == null) {
            return;
        }
        if (node.getPackageName() == null || SYSTEM_APPS.contains(node.getPackageName().toString())) {
            return;
        }
        // 遍历当前节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
            if (childNode != null) {
                addAllClickableNode(nodeList, childNode);
            }
        }

        if (isClickable(node)) {
            // 当前节点是可点击的组件
            // TODO: 在这里执行您的点击操作
            String nodeUniqueId = getUniqueId(node, nodeList.size());
            NodePair nodePair = new NodePair(nodeList.size(), false);
            StrToNodeInfo.put(nodeUniqueId, nodePair);
            nodeList.add(node);
        } else {
            // 当前节点不是可点击的组件
            // TODO: 在这里执行其他操作
//            Log.d(TAG, "不可点击" + res);
        }

    }

    public boolean isClickable(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return false;
        }
        boolean res = false;
        // 判断节点是否可点击
        if (nodeInfo.isClickable()) {
            res = true;
        }
//        // 判断节点是否是 ViewGroup
//        if(nodeInfo.isClickable()){
//            if (nodeInfo.getChildCount() > 0) {
//                CharSequence pkgName = nodeInfo.getClassName();
//                CharSequence clzName = nodeInfo.getPackageName();
//                CharSequence text = nodeInfo.getText();
//                String nodeStr = pkgName + ":" +clzName+":" + text;
//                Log.d(TAG, nodeStr+"  View Group如下：");
//                for (int i = 0; i < nodeInfo.getChildCount(); i++) {
//                    AccessibilityNodeInfo childNode = nodeInfo.getChild(i);
////                    if (isClickable(childNode)) {
//                        CharSequence lpkgName = childNode.getClassName();
//                        CharSequence lclzName = childNode.getPackageName();
//                        CharSequence ltext = childNode.getText();
//                        String str  = lpkgName + ":" + lclzName + ":" + ltext;
//                        Log.d(TAG, "该子节点的可点击组件"+ str);
////                    }
//                }
//            }
//        }
        return res;
    }


    //提取当前窗口的所有文本内容
    // 事件类型为 TYPE_WINDOW_CONTENT_CHANGED
    private void ExtractAllCurrentWindowText(AccessibilityEvent accessibilityEvent) {
        AccessibilityNodeInfo rootNodeInfo = accessibilityEvent.getSource();
        if (rootNodeInfo != null) {
            rootNodeInfo.refresh();
        }
        String str = "********************************************************";
        Log.e(TAG, str);
        // 遍历节点树获取文本信息
        if (rootNodeInfo != null) {
            StringBuilder sb = new StringBuilder();
            traverseNodeTree(rootNodeInfo, sb);
            String allText = sb.toString();
            // 在这里处理获取到的所有文本内容
            Log.e(TAG, allText);
        }
    }

    //当用户点击了文本，告知用户可能泄漏隐私信息
    //事件类型为TYPE_VIEW_FOCUSED | TYPE_VIEW_TEXT_CHANGED
    private void showInforLeakToast(AccessibilityEvent accessibilityEvent) {
        AccessibilityNodeInfo source = accessibilityEvent.getSource();
        if (source != null) {
            source.refresh();
            String className = source.getClassName().toString();
            String resourceId = source.getViewIdResourceName();
            Log.d(TAG, "人工点的" + className);
            CharSequence text = source.getText();
            if (text != null) {
                String inputText = text.toString();
                // 处理输入框文本内容
                Log.e(TAG, inputText);
                Toast toast = Toast.makeText(getApplicationContext(), "目前输入的信息可能被收集", Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }


    // Get Unique Node Id  唯一标识一个组件
    private String getUniqueId(AccessibilityNodeInfo nodeInfo, long idx) {
        CharSequence packageName = nodeInfo.getPackageName();
        CharSequence className = nodeInfo.getClassName();
        CharSequence viewId = nodeInfo.getText();
        return packageName + ":" + className + ":" + viewId + ":" + idx;
    }

    private void traverseNodeTree(AccessibilityNodeInfo nodeInfo, StringBuilder sb) {
        if (nodeInfo == null) {
            return;
        }
        String className = nodeInfo.getClassName().toString();
        String resourceId = nodeInfo.getViewIdResourceName();
//        String viewId = nodeInfo.getViewIdResourceName();
        Log.e(TAG, className + " " + resourceId);
        if (nodeInfo.getText() != null) {
            sb.append(nodeInfo.getText());

            sb.append(" ");
        }
        for (int i = 0; i < nodeInfo.getChildCount(); i++) {
            AccessibilityNodeInfo childNodeInfo = nodeInfo.getChild(i);
            traverseNodeTree(childNodeInfo, sb);
        }
    }


    @Override
    public void onInterrupt() {
        Log.e(TAG, "onInterrupt: something went wrong");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        Log.e(TAG, "onServiceConnected");
        //在应用程序启动完成后，服务才会开始接收事件并执行onAccessibilityEvent方法。
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
        setServiceInfo(info);

    }

}
