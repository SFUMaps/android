// MARK: Mercator Projection (class object)

/** @constructor */
function MercatorProjection() {
  this.pixelOrigin_ = new google.maps.Point(TILE_SIZE / 2,
      TILE_SIZE / 2);
  this.pixelsPerLonDegree_ = TILE_SIZE / 360;
  this.pixelsPerLonRadian_ = TILE_SIZE / (2 * Math.PI);
}

MercatorProjection.prototype.fromLatLngToPoint = function(latLng, opt_point) {
  var me = this;
  var point = opt_point || new google.maps.Point(0, 0);
  var origin = me.pixelOrigin_;
  point.x = origin.x + latLng.lng() * me.pixelsPerLonDegree_;
  // Truncating to 0.9999 effectively limits latitude to 89.189. This is
  // about a third of a tile past the edge of the world tile.
  var siny = this.bound(Math.sin(this.degreesToRadians(latLng.lat())), -0.9999,
      0.9999);
  point.y = origin.y + 0.5 * Math.log((1 + siny) / (1 - siny)) * -me.pixelsPerLonRadian_;
  return point;
};

MercatorProjection.prototype.fromPointToLatLng = function(point) {
  var me = this;
  var origin = me.pixelOrigin_;
  var lng = (point.x - origin.x) / me.pixelsPerLonDegree_;
  var latRadians = (point.y - origin.y) / -me.pixelsPerLonRadian_;
  var lat = this.radiansToDegrees(2 * Math.atan(Math.exp(latRadians)) -
      Math.PI / 2);
  return new google.maps.LatLng(lat, lng);
};

MercatorProjection.prototype.bound = function(value, opt_min, opt_max) {
  if (opt_min != null) value = Math.max(value, opt_min);
  if (opt_max != null) value = Math.min(value, opt_max);
  return value;
}

MercatorProjection.prototype.degreesToRadians = function(deg) {
  return deg * (Math.PI / 180);
}

MercatorProjection.prototype.radiansToDegrees = function(rad) {
  return rad / (Math.PI / 180);
}

MercatorProjection.prototype.getNormalizedCoord = function(coord, zoom) {
    var y = coord.y;
    var x = coord.x;

    // tile range in one direction range is dependent on zoom level
    // 0 = 1 tile, 1 = 2 tiles, 2 = 4 tiles, 3 = 8 tiles, etc
    var tileRange = 1 << zoom;

    /**
    * If we don't want to repeat in any direction
    * simply return null.
    */
    // don't repeat across y-axis (vertically)
    if (y < 0 || y >= tileRange) return null
    // repeat across x-axis (horizontally)
    if (x < 0 || x >= tileRange) {
      x = (x % tileRange + tileRange) % tileRange
    }

    return {
      x: x,
      y: y
    };
}


// MARK: Custom Map Tools (module)

var MapTools = (function() {

  return {
    addMarker: function(pos, title, iconPath){
      iconPath = iconPath || ""
      title = title || ""
      var marker = new google.maps.Marker({
          position: pos,
          map: _Map,
          icon: iconPath,
          title: title,
      });

      marker.addListener('click', function(e) {
        onMarkerClick(this);
      });

      return marker;
    },

    seperateByKeys: function(data, keys, keyIndex){
      // seperate the data under each key by that key @ keyIndex
      var seperated={}
      for (i in keys) {
        seperated[keys[i]]=[]
      }
      for (i in data) {
        seperated[data[i][keyIndex]].push(data[i])
      }

      return seperated
    }
  }
}());
