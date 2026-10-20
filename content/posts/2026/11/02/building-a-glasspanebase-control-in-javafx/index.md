---
title: "Building a GlassPaneBase Control in JavaFX"
description: "How to implement a reusable glassmorphism surface in JavaFX using the Control–Skin architecture."
date: "2026-11-02"
authors: ["liban-bande-gonzalez"]
categories: ["JavaFX", "Library"]
image: "glass.jpg"
related_posts:
  - "announcing-vitra-modern-ui-library-for-javafx"
  - "building-a-javafx-code-editor-control"
  - "announcing-skinning-javafx-applications"
  - "high-performance-rendering-in-javafx"
---

Glassmorphism remains one of the most requested visual styles in modern desktop applications. Soft translucency, subtle depth and clean borders create a sense of polish that opaque panels rarely achieve.

JavaFX does not provide a built-in glass surface. Creating one that feels professional requires more than a semi-transparent background and a blur effect. It needs a clean public API, a well-structured skin, and the ability to host real content.

This article shows how to build a reusable `GlassPaneBase` control that can serve as the foundation for cards, side panels, dialogs and any surface that should look like frosted glass. The same approach is used in the Vitra UI library.

## Control API

This is the basic structure of the control because it requires a lot of code. All visual responsibility lives in the skin.

```java
public class GlassPaneBase extends Control {
    // Regular variables
    private final ObservableList<Node> glassChildren = FXCollections.observableArrayList();
    private final ObjectProperty<Node> backdropSource = new SimpleObjectProperty<>(this, "backdropSource");
    private final BooleanProperty liveBackdrop = new SimpleBooleanProperty(this, "liveBackdrop", false);
    private final ReadOnlyIntegerWrapper backdropRevision = new ReadOnlyIntegerWrapper(this, "backdropRevision", 0);
    @Override
    protected Skin<?> createDefaultSkin() {
        return new GlassPaneBaseSkin(this);
    }
    //More code goes here.
}
```

This separation keeps application code clean and makes the control easy to extend later.

## Skin structure

The visual tree is composed of several layered regions:

```java
public class GlassPaneBaseSkin extends SkinBase<GlassPaneBase> {

    private static final double LIVE_FRAME_NANOS = 1_000_000_000.0 / 30.0;
    //The root of the glass pane.
    protected final Pane layerStack = new Pane();
    //Flat frosted color.
    protected final Rectangle blurSource = new Rectangle();
    //A tint for the blurred image.
    protected final Rectangle tintLayer = new Rectangle();
    //The borders of the glass pane.
    protected final Rectangle borderLayer = new Rectangle();
    //The blurred picture of what is behind the pane.
    protected final ImageView backdropView = new ImageView();
    protected final Pane contentLayer = new Pane();
    //Rounded clips of the picture and of the content. They are applied to the layers, not to the control, so the
    //drop shadow of the layer stack is not cut off.
    private final Rectangle backdropClip = new Rectangle();
    //The clip is on this holder, not on the blurred picture itself: that way the blur never softens the rounded edge.
    private final Pane backdropHolder = new Pane();
    private final Rectangle contentClip = new Rectangle();
    private final AnimationTimer liveTimer;
    private long lastLiveFrame;
    private boolean refreshPending;

    private final ListChangeListener<Object> glassChildrenListener = 
        change -> contentLayer.getChildren().setAll(getSkinnable().getGlassChildren());
    private double pad;

    public GlassPaneBaseSkin(GlassPaneBase control) {
        super(control);
        blurSource.setFill(Color.web("#B8C6DB"));
        borderLayer.setFill(Color.TRANSPARENT);
        borderLayer.setStrokeWidth(1.0);
        borderLayer.setStroke(Color.WHITE.deriveColor(0, 1, 1, control.getBorderOpacity()));
        backdropView.setPreserveRatio(false);
        backdropView.setVisible(false);
        backdropHolder.getChildren().add(backdropView);
        backdropHolder.setClip(backdropClip);
        contentLayer.setClip(contentClip);
        layerStack.getChildren().addAll(backdropHolder, blurSource, tintLayer, borderLayer, contentLayer);
        layerStack.getStyleClass().add("glass-pane-layers");
        DropShadow shadow = new DropShadow();
        shadow.setRadius(20);
        shadow.setColor(Color.web("#000000", 0.18));
        shadow.setOffsetY(6);
        layerStack.setEffect(shadow);
        getChildren().add(layerStack);
        //Children added before the skin exists (the skin is created lazily) must be shown too.
        contentLayer.getChildren().setAll(control.getGlassChildren());
        control.getGlassChildren().addListener(glassChildrenListener);
        //Bind visual state to the control's styleable properties.
        registerChangeListener(control.blurRadiusProperty(), o -> {
            applyBlurEffect();
            scheduleRefresh();
        });
        registerChangeListener(control.tintColorProperty(), o -> updateTint());
        registerChangeListener(control.tintOpacityProperty(), o -> updateTint());
        registerChangeListener(control.borderOpacityProperty(), o -> updateBorder());
        registerChangeListener(control.glassCornerRadiusProperty(), o -> {
            updateCorners();
            updateClips();
        });
        //Anything that changes what is behind the pane, or where the pane is, needs a new picture.
        registerChangeListener(control.layoutXProperty(), o -> scheduleRefresh());
        registerChangeListener(control.layoutYProperty(), o -> scheduleRefresh());
        registerChangeListener(control.widthProperty(), o -> scheduleRefresh());
        registerChangeListener(control.heightProperty(), o -> scheduleRefresh());
        registerChangeListener(control.parentProperty(), o -> scheduleRefresh());
        registerChangeListener(control.backdropSourceProperty(), o -> scheduleRefresh());
        registerChangeListener(control.backdropRevisionProperty(), o -> scheduleRefresh());
        registerChangeListener(control.sceneProperty(), o -> {
            updateLiveTimer();
            scheduleRefresh();
        });
        registerChangeListener(control.liveBackdropProperty(), o -> updateLiveTimer());
        liveTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastLiveFrame >= LIVE_FRAME_NANOS) {
                    lastLiveFrame = now;
                    refreshBackdrop();
                }
            }
        };
        applyBlurEffect();
        updateTint();
        updateBorder();
        updateCorners();
        updateLiveTimer();
        scheduleRefresh();     
    }

    private void scheduleRefresh() {
        if (refreshPending) {
            return;
        }
        refreshPending = true;
        Platform.runLater(() -> {
            refreshPending = false;
            refreshBackdrop();
        });
    }

    private void refreshBackdrop() {
        GlassPaneBase control = getSkinnable();
        if (control == null) {
            return;
        }
        Node source = control.getBackdropSource() != null ? control.getBackdropSource() : control.getParent();
        double w = layerStack.getWidth();
        double h = layerStack.getHeight();
        if (source == null || control.getScene() == null || source.getScene() != control.getScene() || w <= 0 || h <= 0) {
            showFallback();
            return;
        }
        double radius = Math.max(0, Math.min(255, control.getBlurRadius()));
        pad = Math.ceil(radius * 2);
        //Where the pane is, expressed in the coordinates of the node that is going to be photographed.
        Bounds area = source.sceneToLocal(control.localToScene(control.getLayoutBounds()));
        if (area == null || area.getWidth() <= 0 || area.getHeight() <= 0) {
            showFallback();
            return;
        }
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        params.setViewport(new Rectangle2D(area.getMinX() - pad, area.getMinY() - pad,
                area.getWidth() + 2 * pad, area.getHeight() + 2 * pad));
        //Hide this pane while the picture is taken, so it does not appear inside its own backdrop.
        boolean wasVisible = layerStack.isVisible();
        layerStack.setVisible(false);
        WritableImage picture;
        try {
            picture = source.snapshot(params, null);
        } finally {
            layerStack.setVisible(wasVisible);
        }
        backdropView.setImage(picture);
        backdropView.setVisible(true);
        blurSource.setVisible(false);
        updateClips();
    }
    //More code goes here.
}
```

## Usage example

When the GlassPane is ready the following code can be executed:

```java
    public void glass() {
        Stage primaryStage = new Stage();

        StackPane backdropContent = new StackPane();
        String image="Image.jpg";
        BackgroundImage backgroundImage=new BackgroundImage(new Image(image),BackgroundRepeat.SPACE,
                BackgroundRepeat.SPACE,
                BackgroundPosition.DEFAULT,new BackgroundSize(100, 100, true, true, false, true));
        Background background=new Background(backgroundImage);
        backdropContent.setBackground(background);
        
        Label label = new Label("JAVA FX\nGLASS");
        label.setStyle("-fx-text-fill: purple; -fx-font-size: 40px; -fx-font-weight: bold; -fx-text-alignment: center;");

        GlassPaneBase glassPaneBase = new GlassPaneBase();
        glassPaneBase.addChild(label);
        glassPaneBase.setMaxSize(400,300);

        backdropContent.getChildren().add(glassPaneBase);

        Scene scene = new Scene(backdropContent, 1000, 700);
        primaryStage.setScene(scene);
        primaryStage.setTitle("CustomControls.Glassmorphism Pro Interface");
        primaryStage.show();
    }

```

## Final thoughts
`GlassPaneBase` is one of several panes and controls included in **Vitra**, a UI library created for developers who need modern JavaFX applications without rebuilding every advanced control from scratch.

You can explore the full set of controls at [skinning-javafx-applications.netlify.app](https://skinning-javafx-applications.netlify.app/).
