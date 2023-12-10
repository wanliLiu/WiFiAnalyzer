from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route('/infoCollect', methods=['POST'])
def post_json():
    try:
        json_data = request.get_json()
        print(json_data)
        return jsonify({'success': True, 'data': json_data}), 200
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 400

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080, debug=True)
